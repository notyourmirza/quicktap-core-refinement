<?php
declare(strict_types=1);

namespace Admin;

final class Csrf
{
    public static function token(): string
    {
        $t = Session::get('csrf');
        if (!is_string($t) || $t === '') {
            $t = bin2hex(random_bytes(32));
            Session::set('csrf', $t);
        }
        return $t;
    }

    public static function field(): string
    {
        return '<input type="hidden" name="_csrf" value="' . htmlspecialchars(self::token(), ENT_QUOTES) . '">';
    }

    /** Verifies POST token; aborts with 419 on mismatch. */
    public static function verify(): void
    {
        $sent = (string) ($_POST['_csrf'] ?? '');
        if ($sent === '' || !hash_equals(self::token(), $sent)) {
            http_response_code(419);
            exit('CSRF token mismatch. Please reload the page and try again.');
        }
    }
}
