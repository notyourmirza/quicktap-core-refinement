<?php
declare(strict_types=1);

/**
 * QuickTap POS REST API — front controller.
 * All endpoints are versioned under /v1.
 *
 * Deploy on Hostinger:  public_html/api/  (this folder)
 * Base URL example:     https://your-domain.com/api/v1/...
 */

use QuickTap\Controllers\{AuthController, BackupController, ConfigController, LicenseController, MarketController, ReportController, ResourceController, SyncController};
use QuickTap\Core\{Auth, Config, Request, Response, Router};

require __DIR__ . '/bootstrap.php';

Response::sendHeaders();
Config::load(__DIR__ . '/config.php');

if (Config::get('force_https') && ($_SERVER['HTTPS'] ?? '') !== 'on'
    && ($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '') !== 'https'
    && PHP_SAPI !== 'cli') {
    Response::error('HTTPS required', 403, null, 'HTTPS_REQUIRED');
}

$request = new Request();
$router  = new Router();

$auth     = new AuthController();
$config   = new ConfigController();
$resource = new ResourceController();
$sync     = new SyncController();
$reports  = new ReportController();
$backup   = new BackupController();
$market   = new MarketController();
$license  = new LicenseController();

// ---- public (API-key protected, no session) --------------------------------
$router->get('/v1/ping', function () {
    Response::ok(['status' => 'up', 'version' => '1.0.0']);
});
$router->get('/v1/version', [$config, 'version']);
$router->post('/v1/version', [$config, 'version']);
$router->post('/v1/auth/login', [$auth, 'login']);
$router->post('/v1/auth/refresh', [$auth, 'refresh']);
$router->post('/v1/auth/register', [$license, 'register']);
$router->get('/v1/app/config', [$license, 'appConfig']);

// ---- licence (session required, licence NOT required) ----------------------
$router->get('/v1/license/status', [$license, 'status']);
$router->post('/v1/license/status', [$license, 'status']);
$router->post('/v1/license/verify', [$license, 'verify']);
$router->post('/v1/license/confirm', [$license, 'confirm']);

// ---- authenticated ----------------------------------------------------------
$router->get('/v1/auth/me', [$auth, 'me']);
$router->post('/v1/auth/logout', [$auth, 'logout']);
$router->post('/v1/auth/fingerprint', [$auth, 'fingerprint']);
$router->post('/v1/auth/unlock', [$auth, 'unlock']);

$router->get('/v1/theme', [$config, 'theme']);
$router->get('/v1/settings', [$config, 'settings']);
$router->get('/v1/splash', [$config, 'splash']);
$router->post('/v1/settings/app-name', [$config, 'appName']);
$router->get('/v1/notifications', [$config, 'notifications']);

$router->get('/v1/products', [$resource, 'products']);
$router->post('/v1/products', [$resource, 'saveProduct']);
$router->delete('/v1/products/{uuid}', [$resource, 'deleteProduct']);
$router->get('/v1/categories', [$resource, 'categories']);
$router->post('/v1/categories', [$resource, 'saveCategory']);
$router->get('/v1/customers', [$resource, 'customers']);
$router->post('/v1/customers', [$resource, 'saveCustomer']);
$router->get('/v1/orders', [$resource, 'orders']);
$router->post('/v1/orders', [$resource, 'createOrder']);
$router->get('/v1/expenses', [$resource, 'expenses']);
$router->post('/v1/expenses', [$resource, 'saveExpense']);

$router->get('/v1/sync/pull', [$sync, 'pull']);
$router->post('/v1/sync/push', [$sync, 'push']);

$router->get('/v1/reports/summary', [$reports, 'summary']);
$router->get('/v1/reports/daily', [$reports, 'daily']);
$router->get('/v1/reports/top-products', [$reports, 'topProducts']);
$router->get('/v1/reports/low-stock', [$reports, 'lowStock']);

$router->get('/v1/subscription', [$market, 'plans']);
$router->get('/v1/plans', [$market, 'plans']);
$router->post('/v1/market/request', [$market, 'request']);
$router->get('/v1/market/requests', [$market, 'requests']);

$router->post('/v1/backup/register', [$backup, 'register']);
$router->get('/v1/backup/latest', [$backup, 'latest']);
$router->get('/v1/backup/history', [$backup, 'history']);
$router->post('/v1/backup/restored', [$backup, 'markRestored']);

// ---- run --------------------------------------------------------------------
try {
    Auth::requireApiClient($request);
    $router->dispatch($request);
} catch (Throwable $e) {
    error_log('[quicktap-api] ' . $e->getMessage() . ' @ ' . $e->getFile() . ':' . $e->getLine());
    Response::error('Server error', 500, Config::get('debug') ? $e->getMessage() : null, 'SERVER_ERROR');
}
