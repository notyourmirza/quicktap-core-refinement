<?php
declare(strict_types=1);

namespace QuickTap\Core;

/**
 * Fixed-window rate limiter backed by the `api_rate_limits` table.
 *
 * Used to protect registration, login and licence verification from
 * brute-force / abuse. Fails OPEN only when the table is missing (so an
 * un-migrated database never takes the API down) — never on a limit hit.
 */
final class RateLimit
{
    /**
     * @param string $key      logical bucket, e.g. "license.verify"
     * @param string $subject  ip / device / user discriminator
     * @param int    $max      allowed hits per window
     * @param int    $seconds  window length
     */
    public static function hit(string $key, string $subject, int $max, int $seconds = 60): void
    {
        $bucket = substr($key . '|' . hash('sha256', $subject) . '|' . (int) floor(time() / $seconds), 0, 191);
        try {
            Database::run(
                'INSERT INTO api_rate_limits (bucket, window_start, hits) VALUES (:b, NOW(), 1)
                 ON DUPLICATE KEY UPDATE hits = hits + 1',
                ['b' => $bucket]
            );
            $row = Database::first('SELECT hits FROM api_rate_limits WHERE bucket = :b', ['b' => $bucket]);
        } catch (\Throwable) {
            return; // table not migrated yet — do not break the API
        }

        // Opportunistic cleanup (cheap, ~1% of requests).
        if (random_int(1, 100) === 1) {
            try {
                Database::run('DELETE FROM api_rate_limits WHERE window_start < (NOW() - INTERVAL 1 DAY)');
            } catch (\Throwable) {
            }
        }

        if ((int) ($row['hits'] ?? 0) > $max) {
            Response::error('Too many requests. Please slow down and try again shortly.',
                429, null, 'RATE_LIMITED');
        }
    }
}
