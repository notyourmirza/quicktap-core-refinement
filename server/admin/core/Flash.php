<?php
declare(strict_types=1);

namespace Admin;

final class Flash
{
    public static function add(string $type, string $message): void
    {
        $all   = $_SESSION['flash'] ?? [];
        $all[] = ['type' => $type, 'message' => $message];
        $_SESSION['flash'] = $all;
    }

    public static function success(string $m): void { self::add('success', $m); }
    public static function error(string $m): void   { self::add('danger', $m); }
    public static function info(string $m): void    { self::add('info', $m); }

    /** @return array<int,array{type:string,message:string}> */
    public static function pull(): array
    {
        $all = $_SESSION['flash'] ?? [];
        unset($_SESSION['flash']);
        return is_array($all) ? $all : [];
    }
}
