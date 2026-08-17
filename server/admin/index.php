<?php
declare(strict_types=1);

/**
 * QuickTap POS — Super Admin Panel front controller.
 * All pages route through here: index.php?p=<page>
 */

require __DIR__ . '/bootstrap.php';

use Admin\AdminAuth;
use Admin\Csrf;

header('X-Frame-Options: DENY');
header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: same-origin');
header('X-XSS-Protection: 0');

$routes = [
    'login'         => 'Sign in',
    'logout'        => 'Sign out',
    'dashboard'     => 'Dashboard',
    'shops'         => 'Shops',
    'shop'          => 'Shop details',
    'devices'       => 'Devices',
    'licenses'      => 'Licences',
    'credits'       => 'Credits',
    'plans'         => 'Subscription plans',
    'market_requests' => 'Purchase requests',
    'whatsapp'      => 'WhatsApp support',
    'splash'        => 'Splash screen',
    'branding'      => 'Branding',
    'receipts'      => 'Receipt designs',
    'versions'      => 'App versions',
    'notifications' => 'Notifications',
    'backups'       => 'Backups',
    'reports'       => 'Reports',
    'logs'          => 'Activity logs',
    'admins'        => 'Super admins',
    'api_clients'   => 'API clients',
    'profile'       => 'My profile',
];

$page = query('p', 'dashboard');
if (!isset($routes[$page])) {
    $page = 'dashboard';
}

if (is_post()) {
    Csrf::verify();
}

if (!in_array($page, ['login', 'logout'], true)) {
    AdminAuth::requireLogin();
}

$pageTitle  = $routes[$page];
$activePage = $page;

$file = __DIR__ . '/pages/' . $page . '.php';
if (!is_file($file)) {
    http_response_code(404);
    exit('Page not found');
}

ob_start();
require $file;
$content = (string) ob_get_clean();

require __DIR__ . '/views/layout.php';
