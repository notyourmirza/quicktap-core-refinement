<?php
/**
 * QuickTap POS API — configuration.
 *
 * Copy this file to config.php on the server and fill in real values.
 * NEVER commit real credentials.
 */

return [
    // ---- Database (Hostinger MySQL) ----
    'db' => [
        'host'     => 'localhost',
        'name'     => 'u000000000_quicktap',
        'user'     => 'u000000000_quicktap',
        'pass'     => 'CHANGE_ME',
        'charset'  => 'utf8mb4',
    ],

    // ---- Security ----
    'jwt' => [
        // 64+ random chars. Generate: openssl rand -hex 32
        'secret'            => 'CHANGE_ME_JWT_SECRET',
        'issuer'            => 'quicktap-pos',
        'access_ttl'        => 3600,        // 1 hour
        'refresh_ttl'       => 60 * 60 * 24 * 30, // 30 days
    ],

    // AES-256 key for device-id encryption. Generate: openssl rand -hex 32
    'encryption_key' => 'CHANGE_ME_ENCRYPTION_KEY',

    // ---- API clients (app must send X-App-Id / X-Api-Key / X-Api-Secret) ----
    // Rows also live in api_clients; this is the bootstrap fallback.
    'require_api_key' => true,

    // ---- Behaviour ----
    'force_https'      => true,
    'debug'            => false,
    'max_login_attempts' => 5,
    'lockout_minutes'    => 15,
    'timezone'         => 'Asia/Karachi',

    // Uploads (logo / splash)
    'upload_dir' => __DIR__ . '/../uploads',
    'upload_url' => '/uploads',
];
