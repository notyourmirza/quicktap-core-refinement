<?php
declare(strict_types=1);

namespace QuickTap\Core;

/** Loads config.php once and exposes dotted access. */
final class Config
{
    /** @var array<string,mixed>|null */
    private static ?array $data = null;

    public static function load(string $path): void
    {
        if (!is_file($path)) {
            Response::error('Server not configured: config.php missing', 500);
        }
        self::$data = require $path;
        date_default_timezone_set(self::$data['timezone'] ?? 'UTC');
    }

    public static function get(string $key, mixed $default = null): mixed
    {
        $parts = explode('.', $key);
        $node  = self::$data ?? [];
        foreach ($parts as $part) {
            if (!is_array($node) || !array_key_exists($part, $node)) {
                return $default;
            }
            $node = $node[$part];
        }
        return $node;
    }
}
