<?php
declare(strict_types=1);

namespace Admin;

use QuickTap\Core\Database;

/**
 * Tamper-evident trail of every privileged change (licence, credits, blocks).
 * Separate from activity_logs: this table stores the before/after values so a
 * dispute can always be settled from the server side.
 */
final class AdminAudit
{
    public static function write(
        string $action,
        string $entity,
        ?string $entityId = null,
        ?string $oldValue = null,
        ?string $newValue = null,
        array $meta = []
    ): void {
        try {
            Database::run(
                'INSERT INTO admin_audit_logs (admin_id, action, entity, entity_id, old_value, new_value, meta_json, ip, user_agent)
                 VALUES (?,?,?,?,?,?,?,?,?)',
                [
                    AdminAuth::id() ?: null,
                    $action,
                    $entity,
                    $entityId,
                    $oldValue,
                    $newValue,
                    $meta ? json_encode($meta, JSON_UNESCAPED_UNICODE) : null,
                    substr((string) ($_SERVER['REMOTE_ADDR'] ?? ''), 0, 45),
                    substr((string) ($_SERVER['HTTP_USER_AGENT'] ?? ''), 0, 255),
                ]
            );
        } catch (\Throwable) {
            // auditing must never break the request
        }
    }
}
