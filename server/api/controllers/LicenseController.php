<?php
declare(strict_types=1);

namespace QuickTap\Controllers;

use QuickTap\Core\{Auth, Config, Crypto, Database, Jwt, License, RateLimit, Request, Response, Validator};

/**
 * Registration + server-authoritative licensing.
 *
 * Rules enforced here (never on the client):
 *   - one device may create exactly ONE new account
 *   - a licence is only valid when the database says so
 *   - every expiry is calculated from the SERVER clock
 *   - the client can neither activate nor extend its own licence
 */
final class LicenseController
{
    // ------------------------------------------------------------ register

    public function register(Request $req): void
    {
        RateLimit::hit('auth.register.ip', $req->ip(), 10, 3600);

        $in = (new Validator($req->body))
            ->required('shop_name', 160)
            ->required('username', 80)
            ->required('password', 200)
            ->optional('full_name', 160, null)
            ->optional('phone', 40, null)
            ->optional('email', 190, null)
            ->required('device_id', 191)
            ->optional('device_name', 191, 'Android device')
            ->optional('app_version', 40, '2.0.0')
            ->optional('os_version', 40, '')
            ->optional('plan_code', 40, null)
            ->validOrFail();

        if (strlen($in['password']) < 6) {
            Response::error('Password must be at least 6 characters.', 422, null, 'WEAK_PASSWORD');
        }
        if (!preg_match('/^[a-zA-Z0-9._-]{3,80}$/', $in['username'])) {
            Response::error('Username may only contain letters, numbers, dot, dash and underscore.',
                422, null, 'BAD_USERNAME');
        }

        $deviceHash = Crypto::fingerprint($in['device_id']);
        RateLimit::hit('auth.register.device', $deviceHash, 5, 86400);

        // ---- ONE DEVICE = ONE NEW ACCOUNT (enforced server-side) ----
        $bound = Database::first(
            'SELECT da.*, s.name AS shop_name FROM device_accounts da
               LEFT JOIN shops s ON s.id = da.shop_id
              WHERE da.device_id = :d LIMIT 1',
            ['d' => $deviceHash]
        );
        if ($bound && $bound['status'] === 'blocked') {
            Response::error('This device has been blocked. Contact support.', 403, null, 'DEVICE_BLOCKED');
        }
        if ($bound && $bound['status'] === 'active') {
            Response::error('This device is already registered with an account.',
                409, null, 'DEVICE_ALREADY_REGISTERED');
        }

        if (Database::first('SELECT id FROM users WHERE username = :u LIMIT 1', ['u' => $in['username']])) {
            Response::error('That username is already taken.', 409, null, 'USERNAME_TAKEN');
        }

        $planId = null;
        if (!empty($in['plan_code'])) {
            $plan = Database::first('SELECT id FROM plans WHERE code = :c AND is_active = 1 LIMIT 1',
                ['c' => $in['plan_code']]);
            $planId = $plan ? (int) $plan['id'] : null;
        }

        $uuid = self::uuid4();
        Database::run(
            'INSERT INTO shops (uuid, name, owner_name, phone, email, plan_id, status, license_status)
             VALUES (:uuid,:name,:owner,:phone,:email,:plan,"pending","PENDING")',
            ['uuid' => $uuid, 'name' => $in['shop_name'], 'owner' => $in['full_name'],
             'phone' => $in['phone'], 'email' => $in['email'], 'plan' => $planId]
        );
        $shopId = (int) Database::insertId();

        Database::run(
            'INSERT INTO users (shop_id, username, password_hash, full_name, role, device_id, device_name, device_bound_at)
             VALUES (:s,:u,:p,:f,"owner",:d,:dn,NOW())',
            ['s' => $shopId, 'u' => $in['username'],
             'p' => password_hash($in['password'], PASSWORD_DEFAULT),
             'f' => $in['full_name'] ?? $in['username'], 'd' => $deviceHash, 'dn' => $in['device_name']]
        );
        $userId = (int) Database::insertId();

        Database::run(
            'INSERT INTO device_accounts (device_id, shop_id, user_id, device_name, app_version, os_version, status)
             VALUES (:d,:s,:u,:n,:av,:ov,"active")
             ON DUPLICATE KEY UPDATE shop_id = VALUES(shop_id), user_id = VALUES(user_id),
                 device_name = VALUES(device_name), app_version = VALUES(app_version),
                 os_version = VALUES(os_version), status = "active"',
            ['d' => $deviceHash, 's' => $shopId, 'u' => $userId, 'n' => $in['device_name'],
             'av' => $in['app_version'], 'ov' => $in['os_version']]
        );

        Database::run(
            'INSERT INTO devices (shop_id, user_id, device_id, device_name, app_version, os_version, last_seen_at)
             VALUES (:s,:u,:d,:n,:av,:ov,NOW())
             ON DUPLICATE KEY UPDATE user_id = VALUES(user_id), last_seen_at = NOW()',
            ['s' => $shopId, 'u' => $userId, 'd' => $deviceHash, 'n' => $in['device_name'],
             'av' => $in['app_version'], 'ov' => $in['os_version']]
        );

        // Locked account + licence request waiting for the Super Admin.
        Database::run(
            'INSERT INTO licenses (shop_id, user_id, device_id, license_key, plan_id, status, duration_days)
             VALUES (:s,:u,:d,:k,:p,"PENDING",0)',
            ['s' => $shopId, 'u' => $userId, 'd' => $deviceHash,
             'k' => License::generateKey(), 'p' => $planId]
        );

        Database::run(
            'INSERT INTO license_requests (shop_id, user_id, device_id, device_name, app_version, os_version, requested_plan_id, status)
             VALUES (:s,:u,:d,:n,:av,:ov,:p,"PENDING")',
            ['s' => $shopId, 'u' => $userId, 'd' => $deviceHash, 'n' => $in['device_name'],
             'av' => $in['app_version'], 'ov' => $in['os_version'], 'p' => $planId]
        );

        Auth::log($shopId, 'user', $userId, 'account_registered', 'device', $in['device_name']);

        $tokens = self::issueTokens($userId, $shopId, 'owner', $deviceHash);
        $license = License::forShop($shopId, $userId);

        Response::ok([
            'user'    => ['id' => $userId, 'username' => $in['username'], 'role' => 'owner'],
            'shop'    => ['id' => $shopId, 'uuid' => $uuid, 'name' => $in['shop_name'], 'currency' => 'Rs'],
            'tokens'  => $tokens,
            'license' => License::payload($license, ['request_status' => 'PENDING']),
            'support' => self::support(),
        ], 'Account created. Your licence is pending activation.');
    }

    // -------------------------------------------------------------- status

    /** Current licence state for the signed-in account. Cheap; safe to poll. */
    public function status(Request $req): void
    {
        $ctx = Auth::requireAccount($req);
        RateLimit::hit('license.status', (string) $ctx['user_id'], 120, 60);

        $license = License::forShop($ctx['shop_id'], $ctx['user_id']);
        $request = Database::first(
            'SELECT status FROM license_requests WHERE shop_id = :s ORDER BY id DESC LIMIT 1',
            ['s' => $ctx['shop_id']]
        );
        $user = Database::first(
            'SELECT is_blocked, credits, license_confirmed_at FROM users WHERE id = :id',
            ['id' => $ctx['user_id']]
        );

        if ($license) {
            Database::run('UPDATE licenses SET last_verified_at = NOW() WHERE id = :id',
                ['id' => (int) $license['id']]);
        }

        Response::ok(License::payload($license, [
            'account_status'  => (int) ($user['is_blocked'] ?? 0) === 1 ? 'BLOCKED' : $ctx['shop_status'],
            'request_status'  => $request['status'] ?? 'NONE',
            'device_bound'    => true,
            'credits'         => (int) ($user['credits'] ?? 0),
            'confirmed'       => !empty($user['license_confirmed_at']),
            'support'         => self::support(),
        ]));
    }

    // -------------------------------------------------------------- verify

    /**
     * The user types the licence key issued by the Super Admin. The server —
     * and only the server — decides whether it unlocks the app, and computes
     * the expiry from its own clock.
     */
    public function verify(Request $req): void
    {
        $ctx = Auth::requireAccount($req);
        RateLimit::hit('license.verify', (string) $ctx['user_id'], 10, 600);
        RateLimit::hit('license.verify.ip', $req->ip(), 40, 600);

        $in = (new Validator($req->body))->required('license_key', 64)->validOrFail();
        $key = strtoupper(trim($in['license_key']));

        $license = Database::first('SELECT * FROM licenses WHERE license_key = :k LIMIT 1', ['k' => $key]);
        if (!$license) {
            Auth::log($ctx['shop_id'], 'user', $ctx['user_id'], 'license_verify_failed', 'license', $key);
            Response::error('Invalid licence key.', 404, null, 'LICENSE_NOT_FOUND');
        }

        // Ownership: the key must belong to this account, or be unassigned.
        $ownerShop = (int) $license['shop_id'];
        if ($ownerShop !== 0 && $ownerShop !== $ctx['shop_id']) {
            Auth::log($ctx['shop_id'], 'user', $ctx['user_id'], 'license_verify_foreign', 'license', $key);
            Response::error('This licence belongs to another account.', 403, null, 'LICENSE_FOREIGN');
        }
        if (!empty($license['device_id']) && !hash_equals((string) $license['device_id'], $ctx['device'])) {
            Response::error('This licence is bound to another device.', 403, null, 'DEVICE_MISMATCH');
        }
        if ((int) ($ctx['is_blocked'] ?? 0) === 1) {
            Response::error('This account has been blocked. Contact support.', 403, null, 'ACCOUNT_BLOCKED');
        }

        $status = License::effectiveStatus($license);
        if ($status === License::REVOKED || $status === License::BLOCKED) {
            Response::error('This licence has been revoked.', 403, null, 'LICENSE_REVOKED');
        }
        if ($status === License::EXPIRED) {
            Response::error('This licence has expired.', 403, null, 'LICENSE_EXPIRED');
        }
        if ($status === License::PENDING) {
            Response::error('This licence has not been activated by the administrator yet.',
                403, null, 'LICENSE_PENDING');
        }

        // First successful verification binds the licence and starts the clock
        // (server time only) when the admin left the expiry open.
        $expires = $license['expires_at'];
        if ($expires === null && (int) $license['duration_days'] > 0) {
            $expires = date('Y-m-d H:i:s', time() + ((int) $license['duration_days'] * 86400));
        }
        Database::run(
            'UPDATE licenses
                SET user_id = COALESCE(user_id, :u), device_id = COALESCE(NULLIF(device_id, ""), :d),
                    activated_at = COALESCE(activated_at, NOW()), expires_at = :e,
                    last_verified_at = NOW(), status = "ACTIVE"
              WHERE id = :id',
            ['u' => $ctx['user_id'], 'd' => $ctx['device'], 'e' => $expires, 'id' => (int) $license['id']]
        );
        Database::run('UPDATE shops SET license_status = "ACTIVE", status = "active",
                          subscription_starts_at = COALESCE(subscription_starts_at, CURDATE()),
                          subscription_ends_at = :e WHERE id = :s',
            ['e' => $expires !== null ? date('Y-m-d', strtotime((string) $expires)) : null, 's' => $ctx['shop_id']]);
        Database::run('UPDATE license_requests SET status = "APPROVED", handled_at = NOW()
                        WHERE shop_id = :s AND status = "PENDING"', ['s' => $ctx['shop_id']]);

        Auth::log($ctx['shop_id'], 'user', $ctx['user_id'], 'license_verified', 'license', $key);

        $fresh = Database::first('SELECT * FROM licenses WHERE id = :id', ['id' => (int) $license['id']]);
        Response::ok(License::payload($fresh, [
            'requires_confirmation' => true,
            'support'               => self::support(),
        ]), 'Licence verified successfully.');
    }

    // ------------------------------------------------------------- confirm

    /**
     * Username + password confirmation after a successful verification.
     * Goes through the existing password hash — no plaintext is ever stored.
     */
    public function confirm(Request $req): void
    {
        $ctx = Auth::requireAccount($req);
        RateLimit::hit('license.confirm', (string) $ctx['user_id'], 10, 600);

        $in = (new Validator($req->body))
            ->required('username', 80)
            ->required('password', 200)
            ->validOrFail();

        $row = Database::first('SELECT username, password_hash FROM users WHERE id = :id', ['id' => $ctx['user_id']]);
        if (!$row || !hash_equals((string) $row['username'], $in['username'])
            || !password_verify($in['password'], (string) $row['password_hash'])) {
            Auth::log($ctx['shop_id'], 'user', $ctx['user_id'], 'license_confirm_failed');
            Response::error('Username or password is incorrect.', 401, null, 'BAD_CREDENTIALS');
        }

        $license = License::forShop($ctx['shop_id'], $ctx['user_id']);
        if (!License::isUnlocked($license)) {
            Response::error('Licence is not active.', 403, null, 'LICENSE_INACTIVE');
        }

        Database::run('UPDATE users SET license_confirmed_at = NOW() WHERE id = :id', ['id' => $ctx['user_id']]);
        Auth::log($ctx['shop_id'], 'user', $ctx['user_id'], 'license_confirmed');

        Response::ok(License::payload($license, ['confirmed' => true]), 'Confirmed. Welcome back!');
    }

    // -------------------------------------------------------- public config

    /**
     * Minimal bootstrap config the app needs BEFORE it has a session:
     * the Super Admin's support number and the server clock.
     * API-key protected, contains no personal data.
     */
    public function appConfig(Request $req): void
    {
        RateLimit::hit('app.config', $req->ip(), 120, 60);
        Response::ok([
            'support'      => self::support(),
            'server_time'  => date('Y-m-d H:i:s'),
            'server_time_ms' => time() * 1000,
            'sync_minutes' => (int) (self::setting('license_sync_minutes') ?? 60),
        ]);
    }

    /**
     * Fixed bootstrap payload: the non-secret Firebase client configuration the
     * app needs to reach Remote Config, plus the current api_base_url as a
     * last-resort fallback. Contains NO licence data and NO server secret.
     */
    public function bootstrap(Request $req): void
    {
        RateLimit::hit('app.bootstrap', $req->ip(), 120, 60);

        $apiBase = trim((string) (self::setting('api_base_url') ?? ''));
        if ($apiBase !== '' && !str_starts_with($apiBase, 'https://')) {
            $apiBase = '';
        }

        Response::ok([
            'firebase' => [
                'project_id' => (string) (self::setting('firebase_project_id') ?? ''),
                'app_id'     => (string) (self::setting('firebase_app_id') ?? ''),
                'api_key'    => (string) (self::setting('firebase_api_key') ?? ''),
                'sender_id'  => (string) (self::setting('firebase_sender_id') ?? ''),
            ],
            'api_base_url'   => $apiBase,
            'server_time_ms' => time() * 1000,
        ]);
    }

    // ------------------------------------------------------------- helpers

    private static function support(): array
    {
        $number = preg_replace('/[^0-9]/', '', (string) (self::setting('support_whatsapp') ?? ''));
        return [
            'whatsapp' => $number,
            'wa_link'  => $number !== '' ? 'https://wa.me/' . $number : '',
        ];
    }

    private static function setting(string $key): ?string
    {
        $row = Database::first(
            'SELECT value FROM app_settings WHERE shop_id IS NULL AND setting_key = :k LIMIT 1',
            ['k' => $key]
        );
        return $row['value'] ?? null;
    }

    private static function issueTokens(int $userId, int $shopId, string $role, string $deviceHash): array
    {
        $access  = Jwt::encode(['sub' => $userId, 'shop' => $shopId, 'role' => $role,
                                'dev' => $deviceHash, 'typ' => 'access']);
        $refresh = Crypto::randomToken(32);
        $ttl     = (int) Config::get('jwt.refresh_ttl', 2592000);

        Database::run(
            'INSERT INTO refresh_tokens (user_id, token_hash, device_id, expires_at) VALUES (:u,:h,:d,:e)',
            ['u' => $userId, 'h' => hash('sha256', $refresh), 'd' => $deviceHash,
             'e' => date('Y-m-d H:i:s', time() + $ttl)]
        );

        return ['access_token' => $access, 'refresh_token' => $refresh, 'token_type' => 'Bearer',
                'expires_in' => (int) Config::get('jwt.access_ttl', 3600)];
    }

    private static function uuid4(): string
    {
        $d = random_bytes(16);
        $d[6] = chr((ord($d[6]) & 0x0f) | 0x40);
        $d[8] = chr((ord($d[8]) & 0x3f) | 0x80);
        return vsprintf('%s%s-%s-%s-%s-%s%s%s', str_split(bin2hex($d), 4));
    }
}
