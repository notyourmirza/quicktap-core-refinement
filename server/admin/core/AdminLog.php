<?php
declare(strict_types=1);

namespace Admin;

use QuickTap\Core\Database;

final class AdminLog
{
    public static function write(string $action, ?string $entity = null, ?string $entityId = null, ?int $shopId = null, array $meta = []): void
    {
        try {
            Database::run(
                'INSERT INTO activity_logs (shop_id, actor_type, actor_id, action, entity, entity_id, meta_json, ip)
                 VALUES (?, "admin", ?, ?, ?, ?, ?, ?)',
                [
                    $shopId,
                    AdminAuth::id() ?: null,
                    $action,
                    $entity,
                    $entityId,
                    $meta ? json_encode($meta, JSON_UNESCAPED_UNICODE) : null,
                    substr((string) ($_SERVER['REMOTE_ADDR'] ?? ''), 0, 45),
                ]
            );
        } catch (\Throwable) {
            // logging must never break a request
        }
    }
}
