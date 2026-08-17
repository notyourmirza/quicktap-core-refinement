<?php
declare(strict_types=1);

namespace QuickTap\Controllers;

use QuickTap\Core\{Auth, Config, Crypto, Database, Jwt, RateLimit, Request, Response, Validator};

/**
 * Login (username + password), device binding, token refresh, logout,
 * fingerprint enrolment flag.
 */
final class AuthController
{
    public function login(Request $req): void
    {
        RateLimit::hit('auth.login.ip', $req->ip(), 30, 300);
        RateLimit::hit('auth.login.user', (string) ($req->body['username'] ?? ''), 10, 300);

        $in = (new Validator($req->body))
            ->required('username', 80)
            ->required('password', 200)
            ->required('device_id', 191)
            ->optional('device_name', 191, 'Android device')
            ->optional('app_version', 40, '2.0.0')
            ->optional('os_version', 40, '')
            ->validOrFail();

        $user = Database::first(
            'SELECT u.*, s.status AS shop_status, s.name AS shop_name, s.currency, s.uuid AS shop_uuid,
                    s.subscription_ends_at
               FROM users u JOIN shops s ON s.id = u.shop_id
              WHERE u.username = :u AND u.deleted_at IS NULL LIMIT 1',
            ['u' => $in['username']]
        );

        if (!$user) {
            Response::error('Invalid username or password', 401, null, 'BAD_CREDENTIALS');
        }
        if ($user['locked_until'] !== null && strtotime($user['locked_until']) > time()) {
            Response::error('Too many failed attempts. Try again later.', 429, null, 'LOCKED');
        }
        if (!password_verify($in['password'], $user['password_hash'])) {
            $attempts = (int) $user['failed_attempts'] + 1;
            $max      = (int) Config::get('max_login_attempts', 5);
            $lock     = $attempts >= $max
                ? date('Y-m-d H:i:s', time() + 60 * (int) Config::get('lockout_minutes', 15))
                : null;
            Database::run('UPDATE users SET failed_attempts = :a, locked_until = :l WHERE id = :id',
                ['a' => $attempts, 'l' => $lock, 'id' => $user['id']]);
            Auth::log((int) $user['shop_id'], 'user', (int) $user['id'], 'login_failed');
            Response::error('Invalid username or password', 401, null, 'BAD_CREDENTIALS');
        }
        if ((int) $user['is_active'] !== 1) {
            Response::error('Account disabled by administrator', 403, null, 'USER_DISABLED');
        }
        if ($user['shop_status'] !== 'active') {
            Response::error('Shop is ' . $user['shop_status'] . '. Contact support.', 403, null, 'SHOP_INACTIVE');
        }

        // ---- device binding ----
        $deviceHash = Crypto::fingerprint($in['device_id']);
        if ($user['device_id'] === null || $user['device_id'] === '') {
            Database::run(
                'UPDATE users SET device_id = :d, device_name = :n, device_bound_at = NOW() WHERE id = :id',
                ['d' => $deviceHash, 'n' => $in['device_name'], 'id' => $user['id']]
            );
            Auth::log((int) $user['shop_id'], 'user', (int) $user['id'], 'device_bound', 'device', $in['device_name']);
        } elseif (!hash_equals($user['device_id'], $deviceHash)) {
            Auth::log((int) $user['shop_id'], 'user', (int) $user['id'], 'device_mismatch');
            Response::error(
                'This account is already bound to another device. Ask your administrator to reset the device.',
                403, null, 'DEVICE_MISMATCH'
            );
        }

        Database::run(
            'INSERT INTO devices (shop_id, user_id, device_id, device_name, app_version, os_version, last_seen_at)
             VALUES (:s,:u,:d,:n,:av,:ov,NOW())
             ON DUPLICATE KEY UPDATE user_id = VALUES(user_id), device_name = VALUES(device_name),
                 app_version = VALUES(app_version), os_version = VALUES(os_version),
                 status = "active", last_seen_at = NOW()',
            ['s' => $user['shop_id'], 'u' => $user['id'], 'd' => $deviceHash,
             'n' => $in['device_name'], 'av' => $in['app_version'], 'ov' => $in['os_version']]
        );

        Database::run('UPDATE users SET failed_attempts = 0, locked_until = NULL, last_login_at = NOW() WHERE id = :id',
            ['id' => $user['id']]);

        $tokens = $this->issueTokens((int) $user['id'], (int) $user['shop_id'], (string) $user['role'], $deviceHash);
        Auth::log((int) $user['shop_id'], 'user', (int) $user['id'], 'login_success');

        Response::ok([
            'user' => [
                'id'       => (int) $user['id'],
                'username' => $user['username'],
                'name'     => $user['full_name'],
                'role'     => $user['role'],
                'fingerprint_enabled' => (bool) $user['fingerprint_enabled'],
                'device_bound'        => true,
                'first_login'         => $user['device_bound_at'] === null,
            ],
            'shop' => [
                'id'       => (int) $user['shop_id'],
                'uuid'     => $user['shop_uuid'],
                'name'     => $user['shop_name'],
                'currency' => $user['currency'],
                'subscription_ends_at' => $user['subscription_ends_at'],
            ],
            'tokens' => $tokens,
        ], 'Login successful');
    }

    public function refresh(Request $req): void
    {
        $in = (new Validator($req->body))->required('refresh_token', 200)->validOrFail();
        $hash = hash('sha256', $in['refresh_token']);

        $row = Database::first(
            'SELECT rt.*, u.shop_id, u.role, u.is_active
               FROM refresh_tokens rt JOIN users u ON u.id = rt.user_id
              WHERE rt.token_hash = :h LIMIT 1',
            ['h' => $hash]
        );
        if (!$row || $row['revoked_at'] !== null || strtotime($row['expires_at']) < time() || (int) $row['is_active'] !== 1) {
            Response::error('Refresh token invalid or expired', 401, null, 'REFRESH_INVALID');
        }

        Database::run('UPDATE refresh_tokens SET revoked_at = NOW() WHERE id = :id', ['id' => $row['id']]);
        $tokens = $this->issueTokens((int) $row['user_id'], (int) $row['shop_id'], (string) $row['role'], (string) $row['device_id']);
        Response::ok(['tokens' => $tokens], 'Token refreshed');
    }

    public function logout(Request $req): void
    {
        $ctx = Auth::requireUser($req);
        Database::run('UPDATE refresh_tokens SET revoked_at = NOW() WHERE user_id = :u AND revoked_at IS NULL',
            ['u' => $ctx['user_id']]);
        Auth::log($ctx['shop_id'], 'user', $ctx['user_id'], 'logout');
        Response::ok(null, 'Signed out');
    }

    /** Enable/disable fingerprint unlock for this user. */
    public function fingerprint(Request $req): void
    {
        $ctx = Auth::requireUser($req);
        $in  = (new Validator($req->body))->boolean('enabled', false)->validOrFail();
        Database::run('UPDATE users SET fingerprint_enabled = :e WHERE id = :id',
            ['e' => $in['enabled'] ? 1 : 0, 'id' => $ctx['user_id']]);
        Auth::log($ctx['shop_id'], 'user', $ctx['user_id'], $in['enabled'] ? 'fingerprint_enabled' : 'fingerprint_disabled');
        Response::ok(['fingerprint_enabled' => $in['enabled']], 'Updated');
    }

    /** Verifies password for unlocking the session lock screen. */
    public function unlock(Request $req): void
    {
        $ctx = Auth::requireUser($req);
        $in  = (new Validator($req->body))->required('password', 200)->validOrFail();
        $row = Database::first('SELECT password_hash FROM users WHERE id = :id', ['id' => $ctx['user_id']]);
        if (!$row || !password_verify($in['password'], $row['password_hash'])) {
            Response::error('Incorrect password', 401, null, 'BAD_CREDENTIALS');
        }
        Response::ok(null, 'Unlocked');
    }

    public function me(Request $req): void
    {
        $ctx = Auth::requireUser($req);
        $row = Database::first(
            'SELECT u.id, u.username, u.full_name, u.role, u.fingerprint_enabled, u.last_login_at,
                    s.name AS shop_name, s.currency, s.subscription_ends_at, p.name AS plan_name
               FROM users u
               JOIN shops s ON s.id = u.shop_id
          LEFT JOIN plans p ON p.id = s.plan_id
              WHERE u.id = :id',
            ['id' => $ctx['user_id']]
        );
        Response::ok($row);
    }

    private function issueTokens(int $userId, int $shopId, string $role, string $deviceHash): array
    {
        $access = Jwt::encode([
            'sub' => $userId, 'shop' => $shopId, 'role' => $role, 'dev' => $deviceHash, 'typ' => 'access',
        ]);
        $refresh = Crypto::randomToken(32);
        $ttl     = (int) Config::get('jwt.refresh_ttl', 2592000);

        Database::run(
            'INSERT INTO refresh_tokens (user_id, token_hash, device_id, expires_at)
             VALUES (:u,:h,:d,:e)',
            ['u' => $userId, 'h' => hash('sha256', $refresh), 'd' => $deviceHash,
             'e' => date('Y-m-d H:i:s', time() + $ttl)]
        );

        return [
            'access_token'  => $access,
            'refresh_token' => $refresh,
            'token_type'    => 'Bearer',
            'expires_in'    => (int) Config::get('jwt.access_ttl', 3600),
        ];
    }
}
