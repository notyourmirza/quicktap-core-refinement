<?php
declare(strict_types=1);

/**
 * QuickTap POS — Super Admin Panel bootstrap.
 * Reuses the API core (Database, Config, Crypto) so there is a single source of truth.
 */

require __DIR__ . '/../api/bootstrap.php';

use QuickTap\Core\Config;

Config::load(__DIR__ . '/../api/config.php');

require __DIR__ . '/core/Session.php';
require __DIR__ . '/core/Csrf.php';
require __DIR__ . '/core/Flash.php';
require __DIR__ . '/core/AdminAuth.php';
require __DIR__ . '/core/Helpers.php';
require __DIR__ . '/core/AdminLog.php';

Admin\Session::start();
