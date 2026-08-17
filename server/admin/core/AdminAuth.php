<?php
declare(strict_types=1);

namespace Admin;

use QuickTap\Core\Database;

final class AdminAuth
{
    private const IDLE_TIMEOUT = 1800; // 30 min auto-lock

    public static function attempt(string $username, string $password): bool
    {
        $row = Database::first(
            'SELECT id, username, password_hash, full_name, is_active FROM super_admins WHERE username = ? LIMIT 1',
            [$username]
        );
        if (!$row || (int) $row['is_active'] !== 1) {
            return false;
        }
        if (!password_verify($password, (string) $row['password_hash'])) {
            return false;
        }
        session_regenerate_id(true);
        Session::set('admin', [
            'id'        => (int) $row['id'],
            'username'  => $row['username'],
            'full_name' => $row['full_name'] ?? $row['username'],
        ]);
        Session::set('last_activity', time());
        Session::set('__born', time());
        Database::run('UPDATE super_admins SET last_login_at = NOW() WHERE id = ?', [(int) $row['id']]);
        AdminLog::write('admin_login', 'super_admin', (string) $row['id']);
        return true;
    }

    /** @return array{id:int,username:string,full_name:string}|null */
    public static function user(): ?array
    {
        $u = Session::get('admin');
        return is_array($u) ? $u : null;
    }

    public static function id(): int
    {
        return (int) (self::user()['id'] ?? 0);
    }

    public static function check(): bool
    {
        if (self::user() === null) {
            return false;
        }
        $last = (int) Session::get('last_activity', 0);
        if ($last > 0 && time() - $last > self::IDLE_TIMEOUT) {
            self::logout();
            Flash::error('Session expired due to inactivity. Please sign in again.');
            return false;
        }
        Session::set('last_activity', time());
        return true;
    }

    public static function requireLogin(): void
    {
        if (!self::check()) {
            redirect('index.php?p=login');
        }
    }

    public static function logout(): void
    {
        if (self::user() !== null) {
            AdminLog::write('admin_logout', 'super_admin', (string) self::id());
        }
        Session::destroy();
    }
}
