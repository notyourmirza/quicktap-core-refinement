<?php
declare(strict_types=1);

namespace QuickTap\Core;

/** AES-256-GCM helper for device IDs and any at-rest secret. */
final class Crypto
{
    private const CIPHER = 'aes-256-gcm';

    private static function key(): string
    {
        $key = (string) Config::get('encryption_key');
        return hash('sha256', $key, true);
    }

    public static function encrypt(string $plain): string
    {
        $iv  = random_bytes(12);
        $tag = '';
        $ct  = openssl_encrypt($plain, self::CIPHER, self::key(), OPENSSL_RAW_DATA, $iv, $tag);
        if ($ct === false) {
            throw new \RuntimeException('Encryption failed');
        }
        return base64_encode($iv . $tag . $ct);
    }

    public static function decrypt(string $encoded): ?string
    {
        $raw = base64_decode($encoded, true);
        if ($raw === false || strlen($raw) < 29) {
            return null;
        }
        $iv  = substr($raw, 0, 12);
        $tag = substr($raw, 12, 16);
        $ct  = substr($raw, 28);
        $out = openssl_decrypt($ct, self::CIPHER, self::key(), OPENSSL_RAW_DATA, $iv, $tag);
        return $out === false ? null : $out;
    }

    /** Deterministic lookup hash — lets us index/compare device IDs without storing them raw. */
    public static function fingerprint(string $value): string
    {
        return hash_hmac('sha256', $value, (string) Config::get('encryption_key'));
    }

    public static function randomToken(int $bytes = 32): string
    {
        return bin2hex(random_bytes($bytes));
    }
}
