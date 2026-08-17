<?php
declare(strict_types=1);

namespace QuickTap\Core;

/** Uniform JSON envelope for every endpoint. */
final class Response
{
    public static function sendHeaders(): void
    {
        header('Content-Type: application/json; charset=utf-8');
        header('X-Content-Type-Options: nosniff');
        header('X-Frame-Options: DENY');
        header('Referrer-Policy: no-referrer');
        header('X-XSS-Protection: 1; mode=block');
        header('Strict-Transport-Security: max-age=31536000; includeSubDomains');
        header('Access-Control-Allow-Origin: *');
        header('Access-Control-Allow-Headers: Content-Type, Authorization, X-App-Id, X-Api-Key, X-Api-Secret, X-Device-Id, X-App-Version');
        header('Access-Control-Allow-Methods: GET, POST, PUT, PATCH, DELETE, OPTIONS');
    }

    public static function json(mixed $data, int $status = 200): never
    {
        http_response_code($status);
        echo json_encode($data, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
        exit;
    }

    public static function ok(mixed $data = null, string $message = 'OK'): never
    {
        self::json([
            'success'   => true,
            'message'   => $message,
            'data'      => $data,
            'timestamp' => (int) round(microtime(true) * 1000),
        ]);
    }

    public static function error(string $message, int $status = 400, mixed $detail = null, string $code = ''): never
    {
        self::json([
            'success'   => false,
            'message'   => $message,
            'code'      => $code,
            'detail'    => $detail,
            'timestamp' => (int) round(microtime(true) * 1000),
        ], $status);
    }
}
