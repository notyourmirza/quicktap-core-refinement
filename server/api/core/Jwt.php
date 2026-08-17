<?php
declare(strict_types=1);

namespace QuickTap\Core;

/**
 * Dependency-free HS256 JWT (encode / decode / verify).
 * Uses hash_equals for constant-time signature comparison.
 */
final class Jwt
{
    public static function encode(array $payload, ?int $ttl = null): string
    {
        $secret = (string) Config::get('jwt.secret');
        $now    = time();
        $claims = array_merge([
            'iss' => Config::get('jwt.issuer', 'quicktap-pos'),
            'iat' => $now,
            'nbf' => $now,
            'exp' => $now + ($ttl ?? (int) Config::get('jwt.access_ttl', 3600)),
            'jti' => bin2hex(random_bytes(8)),
        ], $payload);

        $header  = self::b64(json_encode(['alg' => 'HS256', 'typ' => 'JWT']));
        $body    = self::b64(json_encode($claims));
        $sig     = self::b64(hash_hmac('sha256', "$header.$body", $secret, true));

        return "$header.$body.$sig";
    }

    /** @return array<string,mixed>|null null when invalid or expired */
    public static function decode(string $token): ?array
    {
        $parts = explode('.', $token);
        if (count($parts) !== 3) {
            return null;
        }
        [$header, $body, $sig] = $parts;

        $expected = self::b64(hash_hmac('sha256', "$header.$body", (string) Config::get('jwt.secret'), true));
        if (!hash_equals($expected, $sig)) {
            return null;
        }

        $claims = json_decode(self::unb64($body), true);
        if (!is_array($claims)) {
            return null;
        }
        if (isset($claims['exp']) && time() >= (int) $claims['exp']) {
            return null;
        }
        if (isset($claims['nbf']) && time() < (int) $claims['nbf']) {
            return null;
        }
        return $claims;
    }

    private static function b64(string $raw): string
    {
        return rtrim(strtr(base64_encode($raw), '+/', '-_'), '=');
    }

    private static function unb64(string $enc): string
    {
        return (string) base64_decode(strtr($enc, '-_', '+/'));
    }
}
