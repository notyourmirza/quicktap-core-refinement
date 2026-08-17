<?php
declare(strict_types=1);

namespace QuickTap\Core;

/**
 * Guards: API client key check + JWT auth context.
 */
final class Auth
{
    public static ?array $claims = null;

    /** Validates X-App-Id / X-Api-Key / X-Api-Secret against api_clients. */
    public static function requireApiClient(Request $req): void
    {
        if (!Config::get('require_api_key', true)) {
            return;
        }
        $appId  = (string) $req->header('X-App-Id', '');
        $apiKey = (string) $req->header('X-Api-Key', '');
        $secret = (string) $req->header('X-Api-Secret', '');

        if ($appId === '' || $apiKey === '') {
            Response::error('Missing API credentials', 401, null, 'NO_API_KEY');
        }

        $client = Database::first(
            'SELECT api_key_hash, secret_hash, is_active FROM api_clients WHERE app_id = :app LIMIT 1',
            ['app' => $appId]
        );
        if (!$client || (int) $client['is_active'] !== 1) {
            Response::error('Unknown application', 401, null, 'BAD_APP');
        }
        if (!hash_equals($client['api_key_hash'], hash('sha256', $apiKey))) {
            Response::error('Invalid API key', 401, null, 'BAD_API_KEY');
        }
        if ($client['secret_hash'] !== '' && $secret !== ''
            && !hash_equals($client['secret_hash'], hash('sha256', $secret))) {
            Response::error('Invalid API secret', 401, null, 'BAD_API_SECRET');
        }
    }

    /** Requires a valid access token; returns claims (user_id, shop_id, role, device). */
    /**
     * Authenticated session WITHOUT the licence / shop-status gate.
     * Used by the licence, support-config and theme endpoints so a locked app
     * can still render its banner and reach support. Never use it to serve
     * protected business data.
     */
    public static function requireAccount(Request $req): array
    {
        $token = $req->bearerToken();
        if (!$token) {
            Response::error('Authentication required', 401, null, 'NO_TOKEN');
        }
        $claims = Jwt::decode($token);
        if (!$claims || ($claims['typ'] ?? '') !== 'access') {
            Response::error('Session expired, please sign in again', 401, null, 'TOKEN_EXPIRED');
        }

        $user = Database::first(
            'SELECT u.id, u.shop_id, u.role, u.is_active, u.device_id, u.is_blocked,
                    s.status AS shop_status
               FROM users u JOIN shops s ON s.id = u.shop_id
              WHERE u.id = :id AND u.deleted_at IS NULL LIMIT 1',
            ['id' => (int) ($claims['sub'] ?? 0)]
        );
        if (!$user || (int) $user['is_active'] !== 1) {
            Response::error('Account disabled', 403, null, 'USER_DISABLED');
        }
        if ((int) ($user['is_blocked'] ?? 0) === 1) {
            Response::error('This account has been blocked. Contact support.', 403, null, 'ACCOUNT_BLOCKED');
        }

        // Device binding must still match the token's device.
        $device = (string) ($claims['dev'] ?? '');
        if ($user['device_id'] !== null && $user['device_id'] !== '' && !hash_equals($user['device_id'], $device)) {
            Response::error('This account is bound to another device', 403, null, 'DEVICE_MISMATCH');
        }

        self::$claims = [
            'user_id'     => (int) $user['id'],
            'shop_id'     => (int) $user['shop_id'],
            'role'        => (string) $user['role'],
            'device'      => $device,
            'shop_status' => (string) $user['shop_status'],
            'is_blocked'  => (int) ($user['is_blocked'] ?? 0),
        ];
        return self::$claims;
    }

    /**
     * Full guard for every protected business endpoint: valid session, active
     * shop AND an ACTIVE licence according to the database. A tampered APK can
     * never satisfy this — the decision is made here, on the server.
     */
    public static function requireUser(Request $req): array
    {
        $ctx = self::requireAccount($req);

        if ($ctx['shop_status'] !== 'active') {
            Response::error('Shop subscription is ' . $ctx['shop_status'], 403, null, 'SHOP_INACTIVE');
        }

        try {
            $license = License::forShop($ctx['shop_id'], $ctx['user_id']);
            $status  = License::effectiveStatus($license);
        } catch (\Throwable) {
            $status = License::ACTIVE; // licences table not migrated yet — do not lock existing installs out
        }
        if ($status !== License::ACTIVE) {
            Response::error(
                $status === License::EXPIRED ? 'Your licence has expired.' : 'Your licence is not active.',
                403, null, 'LICENSE_' . $status
            );
        }

        return $ctx;
    }


    public static function requireRole(array $roles): void
    {
        if (!self::$claims || !in_array(self::$claims['role'], $roles, true)) {
            Response::error('Insufficient permissions', 403, null, 'FORBIDDEN');
        }
    }

    public static function log(?int $shopId, string $actorType, ?int $actorId, string $action, ?string $entity = null, ?string $entityId = null, array $meta = []): void
    {
        Database::run(
            'INSERT INTO activity_logs (shop_id, actor_type, actor_id, action, entity, entity_id, meta_json, ip)
             VALUES (:shop,:atype,:aid,:action,:entity,:eid,:meta,:ip)',
            [
                'shop'   => $shopId,
                'atype'  => $actorType,
                'aid'    => $actorId,
                'action' => $action,
                'entity' => $entity,
                'eid'    => $entityId,
                'meta'   => $meta === [] ? null : json_encode($meta),
                'ip'     => (string) ($_SERVER['REMOTE_ADDR'] ?? ''),
            ]
        );
    }
}
