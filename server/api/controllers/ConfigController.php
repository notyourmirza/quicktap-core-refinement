<?php
declare(strict_types=1);

namespace QuickTap\Controllers;

use QuickTap\Core\{Auth, Database, Request, Response};

/**
 * Server-driven theme + settings + feature toggles.
 * Shop-specific rows override the global (shop_id NULL) defaults.
 */
final class ConfigController
{
    public function theme(Request $req): void
    {
        $ctx  = Auth::requireAccount($req);
        Response::ok(self::themeFor($ctx['shop_id']));
    }

    /** Splash screen configuration, fully owned by the Super Admin panel. */
    public function splash(Request $req): void
    {
        $ctx = Auth::requireAccount($req);
        Response::ok(['splash' => self::splashFor($ctx['shop_id'])]);
    }

    /** Lets the shop rename the app from inside the POS settings screen. */
    public function appName(Request $req): void
    {
        $ctx  = Auth::requireAccount($req);
        $name = trim((string) $req->input('app_name', ''));

        if ($name === '' || mb_strlen($name) > 40) {
            Response::error('App name must be between 1 and 40 characters.', 422, null, 'VALIDATION');
        }

        $existing = Database::first('SELECT id FROM themes WHERE shop_id = :s LIMIT 1', ['s' => $ctx['shop_id']]);
        if ($existing) {
            Database::run(
                'UPDATE themes SET app_name = :n, version = version + 1 WHERE id = :id',
                ['n' => $name, 'id' => $existing['id']]
            );
        } else {
            Database::run(
                'INSERT INTO themes (shop_id, app_name, version) VALUES (:s, :n, 1)',
                ['s' => $ctx['shop_id'], 'n' => $name]
            );
        }

        Response::ok(['app_name' => $name, 'theme' => self::themeFor($ctx['shop_id'])]);
    }

    public static function themeFor(int $shopId): array
    {
        $row = Database::first('SELECT * FROM themes WHERE shop_id = :s LIMIT 1', ['s' => $shopId])
            ?: Database::first('SELECT * FROM themes WHERE shop_id IS NULL LIMIT 1');

        // Support contact is published from app_settings so the panel can
        // change it without an app update.
        $settings  = self::settingsFor($shopId);
        $whatsapp  = (string) ($settings['support_whatsapp'] ?? '');

        return [
            'theme_key'        => $row['theme_key']       ?? 'material_you',
            'primary_color'    => $row['primary_color']   ?? '#0E9F6E',
            'secondary_color'  => $row['secondary_color'] ?? '#34D399',
            'logo_url'         => $row['logo_url']        ?? null,
            'splash_url'       => $row['splash_url']      ?? null,
            'app_name'         => $row['app_name']        ?? 'QuickTap POS',
            'receipt_template' => $row['receipt_template'] ?? 'classic',
            'support_whatsapp' => $whatsapp,
            'notifications'    => self::noticesFor($shopId),
            'splash'           => self::splashFor($shopId),
            'version'          => (int) ($row['version']  ?? 1),
            'updated_at'       => $row['updated_at']      ?? null,
        ];
    }

    /**
     * Splash payload: the shop row wins, otherwise the global row, otherwise
     * hardcoded defaults so a fresh install still renders a branded splash.
     *
     * @return array<string,mixed>
     */
    public static function splashFor(int $shopId): array
    {
        $row = null;
        try {
            $row = Database::first('SELECT * FROM splash_config WHERE shop_id = :s LIMIT 1', ['s' => $shopId])
                ?: Database::first('SELECT * FROM splash_config WHERE shop_id IS NULL LIMIT 1');
        } catch (\Throwable) {
            $row = null; // table not migrated yet — fall back to defaults
        }

        return [
            'enabled'          => (bool) (int) ($row['enabled'] ?? 1),
            'title'            => (string) ($row['title'] ?? 'QuickTap POS'),
            'tagline'          => (string) ($row['tagline'] ?? 'Fast. Simple. Reliable.'),
            'credit_prefix'    => (string) ($row['credit_prefix'] ?? 'Powered by'),
            'credit_text'      => (string) ($row['credit_text'] ?? 'MA Technologies'),
            'logo_url'         => $row['logo_url'] ?? null,
            'background_color' => (string) ($row['background_color'] ?? '#0B0F19'),
            'text_color'       => (string) ($row['text_color'] ?? '#FFFFFF'),
            'accent_color'     => (string) ($row['accent_color'] ?? '#0E9F6E'),
            'animation'        => (string) ($row['animation'] ?? 'fade'),
            'duration_ms'      => (int) ($row['duration_ms'] ?? 1800),
            'show_credit'      => (bool) (int) ($row['show_credit'] ?? 1),
            'show_progress'    => (bool) (int) ($row['show_progress'] ?? 1),
            'version'          => (int) ($row['version'] ?? 1),
        ];
    }


    public function settings(Request $req): void
    {
        $ctx = Auth::requireAccount($req);
        Response::ok([
            'theme'    => self::themeFor($ctx['shop_id']),
            'settings' => self::settingsFor($ctx['shop_id']),
            'features' => self::featuresFor($ctx['shop_id']),
            'notices'  => self::noticesFor($ctx['shop_id']),
        ]);
    }

    /** @return array<string,mixed> */
    public static function settingsFor(int $shopId): array
    {
        $rows = Database::all(
            'SELECT setting_key, value, value_type, shop_id FROM app_settings
              WHERE shop_id IS NULL OR shop_id = :s
              ORDER BY shop_id IS NULL DESC',
            ['s' => $shopId]
        );
        $out = [];
        foreach ($rows as $r) {
            $out[$r['setting_key']] = self::cast($r['value'], $r['value_type']);
        }
        return $out;
    }

    /** @return array<string,bool> */
    public static function featuresFor(int $shopId): array
    {
        $rows = Database::all(
            'SELECT feature_key, enabled FROM feature_toggles
              WHERE shop_id IS NULL OR shop_id = :s
              ORDER BY shop_id IS NULL DESC',
            ['s' => $shopId]
        );
        $out = [];
        foreach ($rows as $r) {
            $out[$r['feature_key']] = (bool) $r['enabled'];
        }
        return $out;
    }

    /** @return array<int,array<string,mixed>> */
    public static function noticesFor(int $shopId): array
    {
        return Database::all(
            'SELECT id, title, body, level, starts_at, ends_at FROM notifications
              WHERE is_active = 1
                AND (shop_id IS NULL OR shop_id = :s)
                AND (starts_at IS NULL OR starts_at <= NOW())
                AND (ends_at   IS NULL OR ends_at   >= NOW())
              ORDER BY created_at DESC LIMIT 20',
            ['s' => $shopId]
        );
    }

    /** Notification centre feed for the app. */
    public function notifications(Request $req): void
    {
        $ctx = Auth::requireAccount($req);
        Response::ok(['notifications' => self::noticesFor($ctx['shop_id'])]);
    }

    /** Version check / force update — callable without a session. */
    public function version(Request $req): void
    {
        $code    = (int) ($req->input('version_code', 0));
        $latest  = Database::first('SELECT * FROM app_versions ORDER BY version_code DESC LIMIT 1');
        $maint   = Database::first("SELECT value FROM app_settings WHERE shop_id IS NULL AND setting_key = 'maintenance_mode'");
        $msg     = Database::first("SELECT value FROM app_settings WHERE shop_id IS NULL AND setting_key = 'maintenance_message'");

        if (!$latest) {
            Response::ok(['update_available' => false, 'force_update' => false]);
        }

        $forced = (int) $latest['force_update'] === 1 || $code < (int) $latest['min_supported_code'];

        Response::ok([
            'latest_version_code' => (int) $latest['version_code'],
            'latest_version_name' => $latest['version_name'],
            'update_available'    => $code < (int) $latest['version_code'],
            'force_update'        => $code > 0 && $forced && $code < (int) $latest['version_code'],
            'changelog'           => $latest['changelog'],
            'download_url'        => $latest['download_url'],
            'maintenance_mode'    => (bool) (int) ($maint['value'] ?? 0),
            'maintenance_message' => $msg['value'] ?? '',
        ]);
    }

    private static function cast(?string $value, string $type): mixed
    {
        return match ($type) {
            'int'  => (int) $value,
            'bool' => (bool) (int) $value,
            'json' => json_decode((string) $value, true),
            default => $value,
        };
    }
}
