<?php
declare(strict_types=1);

/**
 * PSR-4-ish autoloader (no Composer needed on shared hosting).
 * QuickTap\Core\Foo      -> core/Foo.php
 * QuickTap\Controllers\X -> controllers/X.php
 */
spl_autoload_register(static function (string $class): void {
    if (!str_starts_with($class, 'QuickTap\\')) {
        return;
    }
    $relative = substr($class, strlen('QuickTap\\'));
    $map = ['Core\\' => 'core/', 'Controllers\\' => 'controllers/', 'Admin\\' => 'admin/'];
    foreach ($map as $prefix => $dir) {
        if (str_starts_with($relative, $prefix)) {
            $file = __DIR__ . '/' . $dir . substr($relative, strlen($prefix)) . '.php';
            if (is_file($file)) {
                require $file;
            }
            return;
        }
    }
});

ini_set('display_errors', '0');
error_reporting(E_ALL);
