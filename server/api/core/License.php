<?php
declare(strict_types=1);

namespace QuickTap\Core;

/**
 * Server-side licence authority.
 *
 * The database is the ONLY place a licence can be declared valid. Nothing the
 * Android client sends (booleans, expiry timestamps, device ids, plan ids) is
 * ever trusted — every value below is read from, or calculated on, the server.
 */
final class License
{
    public const PENDING = 'PENDING';
    public const ACTIVE  = 'ACTIVE';
    public const EXPIRED = 'EXPIRED';
    public const REVOKED = 'REVOKED';
    public const BLOCKED = 'BLOCKED';
    public const SUSPENDED = 'SUSPENDED';

    /** Newest licence row for a shop (optionally narrowed to a user). */
    public static function forShop(int $shopId, ?int $userId = null): ?array
    {
        $row = null;
        if ($userId !== null) {
            $row = Database::first(
                'SELECT * FROM licenses WHERE shop_id = :s AND user_id = :u ORDER BY id DESC LIMIT 1',
                ['s' => $shopId, 'u' => $userId]
            );
        }
        if (!$row) {
            $row = Database::first(
                'SELECT * FROM licenses WHERE shop_id = :s ORDER BY id DESC LIMIT 1',
                ['s' => $shopId]
            );
        }
        return $row ?: null;
    }

    /**
     * Effective status using SERVER time only. Lazily flips ACTIVE -> EXPIRED
     * in the database so the admin panel and the API always agree.
     */
    public static function effectiveStatus(?array $license): string
    {
        if (!$license) {
            return self::PENDING;
        }
        $status = (string) $license['status'];
        if ($status !== self::ACTIVE) {
            return $status;
        }
        $expires = $license['expires_at'] ?? null;
        if ($expires !== null && strtotime((string) $expires) < time()) {
            try {
                Database::run('UPDATE licenses SET status = "EXPIRED" WHERE id = :id AND status = "ACTIVE"',
                    ['id' => (int) $license['id']]);
                Database::run('UPDATE shops SET license_status = "EXPIRED" WHERE id = :s',
                    ['s' => (int) $license['shop_id']]);
            } catch (\Throwable) {
            }
            return self::EXPIRED;
        }
        return self::ACTIVE;
    }

    public static function isUnlocked(?array $license): bool
    {
        return self::effectiveStatus($license) === self::ACTIVE;
    }

    /** Days remaining, server-calculated. NULL = lifetime. */
    public static function daysLeft(?array $license): ?int
    {
        if (!$license || empty($license['expires_at'])) {
            return null;
        }
        return max(0, (int) ceil((strtotime((string) $license['expires_at']) - time()) / 86400));
    }

    /** Human duration text driven by the actual stored duration ("47 Days", "1 Year"). */
    public static function durationLabel(?array $license): string
    {
        if (!$license) {
            return 'Not activated';
        }
        $days = (int) ($license['duration_days'] ?? 0);
        if ($days <= 0) {
            return 'Lifetime';
        }
        if ($days % 365 === 0) {
            $years = intdiv($days, 365);
            return $years === 1 ? '1 Year' : $years . ' Years';
        }
        if ($days % 30 === 0 && $days >= 60) {
            return intdiv($days, 30) . ' Months';
        }
        return $days . ($days === 1 ? ' Day' : ' Days');
    }

    /** Public payload shared by /license/status and /license/verify. */
    public static function payload(?array $license, array $extra = []): array
    {
        $status = self::effectiveStatus($license);
        return array_merge([
            'license_status'   => $status,
            'unlocked'         => $status === self::ACTIVE,
            'license_key'      => $license['license_key'] ?? null,
            'duration_days'    => $license ? (int) $license['duration_days'] : 0,
            'duration_label'   => self::durationLabel($license),
            'activated_at'     => $license['activated_at'] ?? null,
            'expires_at'       => $license['expires_at'] ?? null,
            'expires_at_ms'    => !empty($license['expires_at'])
                ? strtotime((string) $license['expires_at']) * 1000 : 0,
            'days_left'        => self::daysLeft($license),
            'server_time'      => date('Y-m-d H:i:s'),
            'server_time_ms'   => time() * 1000,
        ], $extra);
    }

    public static function generateKey(): string
    {
        $raw = strtoupper(bin2hex(random_bytes(10)));
        return 'QT-' . substr($raw, 0, 5) . '-' . substr($raw, 5, 5)
             . '-' . substr($raw, 10, 5) . '-' . substr($raw, 15, 5);
    }
}
