<?php
declare(strict_types=1);

namespace QuickTap\Controllers;

use QuickTap\Core\{Auth, Database, Request, Response, Validator};

/**
 * Offline-first sync engine.
 *
 * PULL  : GET  /v1/sync/pull?since=<epoch_ms>   -> all rows changed after `since`
 * PUSH  : POST /v1/sync/push                    -> upsert batches, idempotent by uuid
 *
 * Conflict rule: last-write-wins on `updated_at`. The server never overwrites a
 * newer server row with an older client row; rejected rows come back in
 * `conflicts` so the device can refresh them.
 */
final class SyncController
{
    private const ENTITIES = ['categories', 'products', 'customers', 'expenses', 'orders'];

    public function pull(Request $req): void
    {
        $ctx   = Auth::requireUser($req);
        $since = (int) $req->input('since', 0);
        $sinceSql = date('Y-m-d H:i:s', $since > 0 ? (int) ($since / 1000) : 0);

        $data = [];
        foreach (self::ENTITIES as $entity) {
            if ($entity === 'orders') {
                $orders = Database::all(
                    'SELECT * FROM orders WHERE shop_id = :s AND updated_at > :since ORDER BY updated_at ASC LIMIT 2000',
                    ['s' => $ctx['shop_id'], 'since' => $sinceSql]
                );
                foreach ($orders as &$o) {
                    $o['items'] = Database::all('SELECT * FROM order_items WHERE order_id = :o', ['o' => $o['id']]);
                }
                unset($o);
                $data['orders'] = $orders;
                continue;
            }
            $data[$entity] = Database::all(
                "SELECT * FROM {$entity} WHERE shop_id = :s AND updated_at > :since ORDER BY updated_at ASC LIMIT 5000",
                ['s' => $ctx['shop_id'], 'since' => $sinceSql]
            );
        }

        Response::ok([
            'server_time' => (int) round(microtime(true) * 1000),
            'since'       => $since,
            'changes'     => $data,
            'settings'    => ConfigController::settingsFor($ctx['shop_id']),
            'theme'       => ConfigController::themeFor($ctx['shop_id']),
            'features'    => ConfigController::featuresFor($ctx['shop_id']),
        ]);
    }

    public function push(Request $req): void
    {
        $ctx  = Auth::requireUser($req);
        $shop = $ctx['shop_id'];

        $accepted  = [];
        $conflicts = [];

        Database::transaction(function () use ($req, $shop, $ctx, &$accepted, &$conflicts) {
            foreach (self::ENTITIES as $entity) {
                $rows = $req->input($entity, []);
                if (!is_array($rows)) continue;
                $accepted[$entity] = [];
                foreach ($rows as $row) {
                    if (!is_array($row) || empty($row['uuid'])) continue;
                    $method = 'upsert' . ucfirst($entity);
                    $result = $this->{$method}($shop, $ctx, $row);
                    if ($result === 'conflict') {
                        $conflicts[$entity][] = $row['uuid'];
                    } else {
                        $accepted[$entity][] = $row['uuid'];
                    }
                }
            }
        });

        Auth::log($shop, 'user', $ctx['user_id'], 'sync_push', null, null,
            ['counts' => array_map('count', $accepted)]);

        Response::ok([
            'server_time' => (int) round(microtime(true) * 1000),
            'accepted'    => $accepted,
            'conflicts'   => $conflicts,
        ], 'Sync complete');
    }

    // ------------------------------------------------------------------ //

    private function isStale(string $table, int $shop, string $uuid, ?string $clientUpdated): bool
    {
        if ($clientUpdated === null) return false;
        $row = Database::first("SELECT updated_at FROM {$table} WHERE shop_id = :s AND uuid = :u", ['s' => $shop, 'u' => $uuid]);
        if (!$row) return false;
        return strtotime($row['updated_at']) > strtotime($clientUpdated);
    }

    private static function ts(mixed $v): ?string
    {
        if ($v === null || $v === '') return null;
        if (is_numeric($v)) return date('Y-m-d H:i:s', (int) ((float) $v / ((float) $v > 1e11 ? 1000 : 1)));
        $t = strtotime((string) $v);
        return $t ? date('Y-m-d H:i:s', $t) : null;
    }

    private function upsertCategories(int $shop, array $ctx, array $r): string
    {
        $upd = self::ts($r['updated_at'] ?? null);
        if ($this->isStale('categories', $shop, $r['uuid'], $upd)) return 'conflict';
        Database::run(
            'INSERT INTO categories (shop_id, uuid, name, sort_order, updated_at, deleted_at)
             VALUES (:s,:u,:n,:o,COALESCE(:up,NOW()),:d)
             ON DUPLICATE KEY UPDATE name=VALUES(name), sort_order=VALUES(sort_order),
                 updated_at=VALUES(updated_at), deleted_at=VALUES(deleted_at)',
            ['s' => $shop, 'u' => $r['uuid'], 'n' => (string) ($r['name'] ?? 'Unnamed'),
             'o' => (int) ($r['sort_order'] ?? 0), 'up' => $upd, 'd' => self::ts($r['deleted_at'] ?? null)]
        );
        return 'ok';
    }

    private function upsertProducts(int $shop, array $ctx, array $r): string
    {
        $upd = self::ts($r['updated_at'] ?? null);
        if ($this->isStale('products', $shop, $r['uuid'], $upd)) return 'conflict';
        Database::run(
            'INSERT INTO products (shop_id, uuid, category_uuid, name, sku, barcode, price, cost_price, stock,
                                   track_stock, tax_percent, image_url, is_active, updated_at, deleted_at)
             VALUES (:s,:u,:c,:n,:sku,:bc,:p,:cp,:st,:tr,:tax,:img,:act,COALESCE(:up,NOW()),:del)
             ON DUPLICATE KEY UPDATE category_uuid=VALUES(category_uuid), name=VALUES(name), sku=VALUES(sku),
                 barcode=VALUES(barcode), price=VALUES(price), cost_price=VALUES(cost_price), stock=VALUES(stock),
                 track_stock=VALUES(track_stock), tax_percent=VALUES(tax_percent), image_url=VALUES(image_url),
                 is_active=VALUES(is_active), updated_at=VALUES(updated_at), deleted_at=VALUES(deleted_at)',
            ['s' => $shop, 'u' => $r['uuid'], 'c' => $r['category_uuid'] ?? null,
             'n' => (string) ($r['name'] ?? 'Unnamed'), 'sku' => $r['sku'] ?? null, 'bc' => $r['barcode'] ?? null,
             'p' => (float) ($r['price'] ?? 0), 'cp' => (float) ($r['cost_price'] ?? 0),
             'st' => (float) ($r['stock'] ?? 0), 'tr' => !empty($r['track_stock']) ? 1 : 0,
             'tax' => (float) ($r['tax_percent'] ?? 0), 'img' => $r['image_url'] ?? null,
             'act' => isset($r['is_active']) ? (int) (bool) $r['is_active'] : 1,
             'up' => $upd, 'del' => self::ts($r['deleted_at'] ?? null)]
        );
        return 'ok';
    }

    private function upsertCustomers(int $shop, array $ctx, array $r): string
    {
        $upd = self::ts($r['updated_at'] ?? null);
        if ($this->isStale('customers', $shop, $r['uuid'], $upd)) return 'conflict';
        Database::run(
            'INSERT INTO customers (shop_id, uuid, name, phone, email, address, balance, updated_at, deleted_at)
             VALUES (:s,:u,:n,:p,:e,:a,:b,COALESCE(:up,NOW()),:d)
             ON DUPLICATE KEY UPDATE name=VALUES(name), phone=VALUES(phone), email=VALUES(email),
                 address=VALUES(address), balance=VALUES(balance), updated_at=VALUES(updated_at),
                 deleted_at=VALUES(deleted_at)',
            ['s' => $shop, 'u' => $r['uuid'], 'n' => (string) ($r['name'] ?? 'Walk-in'),
             'p' => $r['phone'] ?? null, 'e' => $r['email'] ?? null, 'a' => $r['address'] ?? null,
             'b' => (float) ($r['balance'] ?? 0), 'up' => $upd, 'd' => self::ts($r['deleted_at'] ?? null)]
        );
        return 'ok';
    }

    private function upsertExpenses(int $shop, array $ctx, array $r): string
    {
        $upd = self::ts($r['updated_at'] ?? null);
        if ($this->isStale('expenses', $shop, $r['uuid'], $upd)) return 'conflict';
        Database::run(
            'INSERT INTO expenses (shop_id, uuid, title, category, amount, note, spent_at, updated_at, deleted_at)
             VALUES (:s,:u,:t,:c,:a,:n,COALESCE(:sp,NOW()),COALESCE(:up,NOW()),:d)
             ON DUPLICATE KEY UPDATE title=VALUES(title), category=VALUES(category), amount=VALUES(amount),
                 note=VALUES(note), spent_at=VALUES(spent_at), updated_at=VALUES(updated_at),
                 deleted_at=VALUES(deleted_at)',
            ['s' => $shop, 'u' => $r['uuid'], 't' => (string) ($r['title'] ?? 'Expense'),
             'c' => $r['category'] ?? null, 'a' => (float) ($r['amount'] ?? 0), 'n' => $r['note'] ?? null,
             'sp' => self::ts($r['spent_at'] ?? null), 'up' => $upd, 'd' => self::ts($r['deleted_at'] ?? null)]
        );
        return 'ok';
    }

    /** Orders are immutable financial records: insert-once, never duplicated. */
    private function upsertOrders(int $shop, array $ctx, array $r): string
    {
        $existing = Database::first('SELECT id, status FROM orders WHERE shop_id = :s AND uuid = :u',
            ['s' => $shop, 'u' => $r['uuid']]);

        if ($existing) {
            // Only status transitions (refund/void) may update an existing order.
            $status = in_array($r['status'] ?? '', ['completed', 'refunded', 'void'], true) ? $r['status'] : null;
            if ($status !== null && $status !== $existing['status']) {
                Database::run('UPDATE orders SET status = :st, updated_at = NOW() WHERE id = :id',
                    ['st' => $status, 'id' => $existing['id']]);
            }
            return 'ok';
        }

        Database::run(
            'INSERT INTO orders (shop_id, uuid, invoice_no, customer_uuid, user_id, device_id, subtotal, discount,
                                 tax, total, paid, change_due, payment_method, status, note, ordered_at)
             VALUES (:s,:u,:inv,:cu,:uid,:dev,:sub,:disc,:tax,:tot,:paid,:chg,:pm,:st,:note,COALESCE(:at,NOW()))',
            ['s' => $shop, 'u' => $r['uuid'], 'inv' => (string) ($r['invoice_no'] ?? $r['uuid']),
             'cu' => $r['customer_uuid'] ?? null, 'uid' => $ctx['user_id'], 'dev' => $ctx['device'],
             'sub' => (float) ($r['subtotal'] ?? 0), 'disc' => (float) ($r['discount'] ?? 0),
             'tax' => (float) ($r['tax'] ?? 0), 'tot' => (float) ($r['total'] ?? 0),
             'paid' => (float) ($r['paid'] ?? 0), 'chg' => (float) ($r['change_due'] ?? 0),
             'pm' => in_array($r['payment_method'] ?? '', ['cash','card','wallet','credit','mixed'], true) ? $r['payment_method'] : 'cash',
             'st' => in_array($r['status'] ?? '', ['completed','refunded','void'], true) ? $r['status'] : 'completed',
             'note' => $r['note'] ?? null, 'at' => self::ts($r['ordered_at'] ?? null)]
        );
        $orderId = Database::insertId();

        foreach (($r['items'] ?? []) as $item) {
            if (!is_array($item)) continue;
            Database::run(
                'INSERT INTO order_items (order_id, shop_id, product_uuid, name, qty, unit_price, discount, tax_percent, line_total)
                 VALUES (:o,:s,:p,:n,:q,:up,:d,:tp,:lt)',
                ['o' => $orderId, 's' => $shop, 'p' => $item['product_uuid'] ?? null,
                 'n' => (string) ($item['name'] ?? 'Item'), 'q' => (float) ($item['qty'] ?? 1),
                 'up' => (float) ($item['unit_price'] ?? 0), 'd' => (float) ($item['discount'] ?? 0),
                 'tp' => (float) ($item['tax_percent'] ?? 0), 'lt' => (float) ($item['line_total'] ?? 0)]
            );
            // Stock decrement for tracked products.
            if (!empty($item['product_uuid'])) {
                Database::run(
                    'UPDATE products SET stock = stock - :q, updated_at = NOW()
                      WHERE shop_id = :s AND uuid = :u AND track_stock = 1',
                    ['q' => (float) ($item['qty'] ?? 1), 's' => $shop, 'u' => $item['product_uuid']]
                );
            }
        }
        return 'ok';
    }
}
