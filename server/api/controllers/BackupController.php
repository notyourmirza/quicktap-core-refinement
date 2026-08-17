<?php
declare(strict_types=1);

namespace QuickTap\Controllers;

use QuickTap\Core\{Auth, Database, Request, Response, Validator};

/**
 * Backup registry. The encrypted SQLite blob itself lives in the user's Google
 * Drive (appDataFolder); the server only stores metadata so a reinstalled
 * device can discover the latest backup right after login.
 */
final class BackupController
{
    public function register(Request $req): void
    {
        $ctx = Auth::requireUser($req);
        $in  = (new Validator($req->body))
            ->required('file_name', 191)
            ->optional('file_id', 191)
            ->inList('provider', ['gdrive', 'server'], 'gdrive')
            ->inList('kind', ['auto', 'manual'], 'auto')
            ->integer('size_bytes', 0, PHP_INT_MAX)
            ->optional('checksum', 64)
            ->boolean('encrypted', true)
            ->validOrFail();

        Database::run(
            'INSERT INTO backups (shop_id, user_id, device_id, provider, file_id, file_name, size_bytes, checksum, encrypted, kind)
             VALUES (:s,:u,:d,:p,:fid,:fn,:sz,:ck,:en,:k)',
            ['s' => $ctx['shop_id'], 'u' => $ctx['user_id'], 'd' => $ctx['device'], 'p' => $in['provider'],
             'fid' => $in['file_id'], 'fn' => $in['file_name'], 'sz' => $in['size_bytes'],
             'ck' => $in['checksum'], 'en' => $in['encrypted'] ? 1 : 0, 'k' => $in['kind']]
        );
        Auth::log($ctx['shop_id'], 'user', $ctx['user_id'], 'backup_created', 'backup', $in['file_name']);
        Response::ok(['id' => Database::insertId()], 'Backup registered');
    }

    /** Latest backup for this shop — used by the auto-restore flow after reinstall. */
    public function latest(Request $req): void
    {
        $ctx = Auth::requireUser($req);
        $row = Database::first(
            'SELECT id, provider, file_id, file_name, size_bytes, checksum, encrypted, kind, created_at
               FROM backups WHERE shop_id = :s ORDER BY created_at DESC LIMIT 1',
            ['s' => $ctx['shop_id']]
        );
        Response::ok(['backup' => $row, 'restore_available' => $row !== null]);
    }

    public function history(Request $req): void
    {
        $ctx = Auth::requireUser($req);
        Response::ok(Database::all(
            'SELECT id, provider, file_id, file_name, size_bytes, kind, created_at
               FROM backups WHERE shop_id = :s ORDER BY created_at DESC LIMIT 50',
            ['s' => $ctx['shop_id']]
        ));
    }

    public function markRestored(Request $req): void
    {
        $ctx = Auth::requireUser($req);
        $in  = (new Validator($req->body))->integer('backup_id', 1)->validOrFail();
        Auth::log($ctx['shop_id'], 'user', $ctx['user_id'], 'backup_restored', 'backup', (string) $in['backup_id']);
        Response::ok(null, 'Restore recorded');
    }
}
