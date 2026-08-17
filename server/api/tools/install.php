<?php
declare(strict_types=1);

/**
 * One-time setup helper.
 *
 *   php tools/install.php "My POS App"
 *
 * Creates an api_clients row and prints the App ID / API Key / Secret to paste
 * into the Android app's API configuration screen. Run from the server shell or
 * temporarily via browser, then DELETE this file.
 */

require __DIR__ . '/../bootstrap.php';

use QuickTap\Core\{Config, Database};

Config::load(__DIR__ . '/../config.php');

$name   = $argv[1] ?? 'QuickTap Android';
$appId  = 'app_' . bin2hex(random_bytes(6));
$apiKey = bin2hex(random_bytes(24));
$secret = bin2hex(random_bytes(24));

Database::run(
    'INSERT INTO api_clients (app_id, name, api_key_hash, secret_hash) VALUES (:a,:n,:k,:s)',
    ['a' => $appId, 'n' => $name, 'k' => hash('sha256', $apiKey), 's' => hash('sha256', $secret)]
);

echo "API client created\n";
echo "-------------------------------------------\n";
echo "X-App-Id     : $appId\n";
echo "X-Api-Key    : $apiKey\n";
echo "X-Api-Secret : $secret\n";
echo "-------------------------------------------\n";
echo "Store these now — they are hashed and cannot be recovered.\n";
