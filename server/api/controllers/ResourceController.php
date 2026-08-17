<?php
declare(strict_types=1);

namespace QuickTap\Controllers;

use QuickTap\Core\{Auth, Database, Request, Response, Validator};

/**
 * Read/write endpoints for products, categories, customers, orders and expenses.
 * These are convenience CRUD endpoints; bulk device traffic goes through /sync.
 */
final class ResourceController
{
    // ---------------- products ----------------
    public function products(Request $req): void
    {
        $ctx    = Auth::requireUser($req);
        $search = trim((string) $req->input('q', ''));
        $limit  = min(500, max(1, (int) $req->input('limit', 200)));
        $offset = max(0, (int) $req->input('offset', 0));

        $sql = 'SELECT * FROM products WHERE shop_id = :s AND deleted_at IS NULL';
        $p   = ['s' => $ctx['shop_id']];
        if ($search !== '') {
            $sql .= ' AND (name LIKE :q OR sku LIKE :q OR barcode LIKE :q)';
            $p['q'] = "%{$search}%";
        }
        $sql .= ' ORDER BY name ASC LIMIT ' . $limit . ' OFFSET ' . $offset;

        Response::ok([
            'items' => Database::all($sql, $p),
            'total' => (int) (Database::first('SELECT COUNT(*) c FROM products WHERE shop_id = :s AND deleted_at IS NULL',
                        ['s' => $ctx['shop_id']])['c'] ?? 0),
        ]);
    }

    public function saveProduct(Request $req): void
    {
        $ctx = Auth::requireUser($req);
        $in  = (new Validator($req->body))
            ->uuid('uuid')
            ->required('name', 190)
            ->optional('sku', 80)
            ->optional('barcode', 80)
            ->uuid('category_uuid', false)
            ->number('price', 0, 1e9)
            ->number('cost_price', 0, 1e9)
            ->number('stock', -1e6, 1e9)
            ->number('tax_percent', 0, 100)
            ->boolean('track_stock', true)
            ->boolean('is_active', true)
            ->optional('image_url', 400)
            ->validOrFail();

        Database::run(
            'INSERT INTO products (shop_id, uuid, category_uuid, name, sku, barcode, price, cost_price, stock,
                                   track_stock, tax_percent, image_url, is_active)
             VALUES (:s,:u,:c,:n,:sku,:bc,:p,:cp,:st,:tr,:tax,:img,:act)
             ON DUPLICATE KEY UPDATE category_uuid=VALUES(category_uuid), name=VALUES(name), sku=VALUES(sku),
                 barcode=VALUES(barcode), price=VALUES(price), cost_price=VALUES(cost_price), stock=VALUES(stock),
                 track_stock=VALUES(track_stock), tax_percent=VALUES(tax_percent), image_url=VALUES(image_url),
                 is_active=VALUES(is_active), updated_at=NOW(), deleted_at=NULL',
            ['s' => $ctx['shop_id'], 'u' => $in['uuid'], 'c' => $in['category_uuid'], 'n' => $in['name'],
             'sku' => $in['sku'], 'bc' => $in['barcode'], 'p' => $in['price'], 'cp' => $in['cost_price'],
             'st' => $in['stock'], 'tr' => $in['track_stock'] ? 1 : 0, 'tax' => $in['tax_percent'],
             'img' => $in['image_url'], 'act' => $in['is_active'] ? 1 : 0]
        );
        Response::ok(['uuid' => $in['uuid']], 'Product saved');
    }

    public function deleteProduct(Request $req, array $params): void
    {
        $ctx = Auth::requireUser($req);
        Database::run('UPDATE products SET deleted_at = NOW(), updated_at = NOW() WHERE shop_id = :s AND uuid = :u',
            ['s' => $ctx['shop_id'], 'u' => $params['uuid']]);
        Response::ok(null, 'Product deleted');
    }

    // ---------------- categories ----------------
    public function categories(Request $req): void
    {
        $ctx = Auth::requireUser($req);
        Response::ok(Database::all(
            'SELECT * FROM categories WHERE shop_id = :s AND deleted_at IS NULL ORDER BY sort_order, name',
            ['s' => $ctx['shop_id']]
        ));
    }

    public function saveCategory(Request $req): void
    {
        $ctx = Auth::requireUser($req);
        $in  = (new Validator($req->body))->uuid('uuid')->required('name', 120)->integer('sort_order')->validOrFail();
        Database::run(
            'INSERT INTO categories (shop_id, uuid, name, sort_order) VALUES (:s,:u,:n,:o)
             ON DUPLICATE KEY UPDATE name=VALUES(name), sort_order=VALUES(sort_order), updated_at=NOW(), deleted_at=NULL',
            ['s' => $ctx['shop_id'], 'u' => $in['uuid'], 'n' => $in['name'], 'o' => $in['sort_order']]
        );
        Response::ok(['uuid' => $in['uuid']], 'Category saved');
    }

    // ---------------- customers ----------------
    public function customers(Request $req): void
    {
        $ctx = Auth::requireUser($req);
        $q   = trim((string) $req->input('q', ''));
        $sql = 'SELECT * FROM customers WHERE shop_id = :s AND deleted_at IS NULL';
        $p   = ['s' => $ctx['shop_id']];
        if ($q !== '') { $sql .= ' AND (name LIKE :q OR phone LIKE :q)'; $p['q'] = "%$q%"; }
        Response::ok(Database::all($sql . ' ORDER BY name ASC LIMIT 500', $p));
    }

    public function saveCustomer(Request $req): void
    {
        $ctx = Auth::requireUser($req);
        $in  = (new Validator($req->body))
            ->uuid('uuid')->required('name', 160)->optional('phone', 40)
            ->email('email', false)->optional('address', 400)->number('balance', -1e9, 1e9)
            ->validOrFail();
        Database::run(
            'INSERT INTO customers (shop_id, uuid, name, phone, email, address, balance)
             VALUES (:s,:u,:n,:p,:e,:a,:b)
             ON DUPLICATE KEY UPDATE name=VALUES(name), phone=VALUES(phone), email=VALUES(email),
                 address=VALUES(address), balance=VALUES(balance), updated_at=NOW(), deleted_at=NULL',
            ['s' => $ctx['shop_id'], 'u' => $in['uuid'], 'n' => $in['name'], 'p' => $in['phone'],
             'e' => $in['email'], 'a' => $in['address'], 'b' => $in['balance']]
        );
        Response::ok(['uuid' => $in['uuid']], 'Customer saved');
    }

    // ---------------- orders ----------------
    public function orders(Request $req): void
    {
        $ctx  = Auth::requireUser($req);
        $from = (string) $req->input('from', date('Y-m-d', strtotime('-30 days')));
        $to   = (string) $req->input('to', date('Y-m-d'));
        $rows = Database::all(
            'SELECT * FROM orders
              WHERE shop_id = :s AND deleted_at IS NULL AND DATE(ordered_at) BETWEEN :f AND :t
              ORDER BY ordered_at DESC LIMIT 1000',
            ['s' => $ctx['shop_id'], 'f' => $from, 't' => $to]
        );
        foreach ($rows as &$r) {
            $r['items'] = Database::all('SELECT * FROM order_items WHERE order_id = :o', ['o' => $r['id']]);
        }
        Response::ok($rows);
    }

    public function createOrder(Request $req): void
    {
        $ctx = Auth::requireUser($req);
        $in  = (new Validator($req->body))
            ->uuid('uuid')->required('invoice_no', 60)->uuid('customer_uuid', false)
            ->number('subtotal', 0)->number('discount', 0)->number('tax', 0)->number('total', 0)
            ->number('paid', 0)->number('change_due', 0)
            ->inList('payment_method', ['cash','card','wallet','credit','mixed'], 'cash')
            ->arrayOf('items')
            ->validOrFail();

        $existing = Database::first('SELECT id FROM orders WHERE shop_id = :s AND uuid = :u',
            ['s' => $ctx['shop_id'], 'u' => $in['uuid']]);
        if ($existing) {
            Response::ok(['uuid' => $in['uuid'], 'duplicate' => true], 'Order already recorded');
        }

        Database::transaction(function () use ($in, $ctx) {
            Database::run(
                'INSERT INTO orders (shop_id, uuid, invoice_no, customer_uuid, user_id, device_id, subtotal,
                                     discount, tax, total, paid, change_due, payment_method, ordered_at)
                 VALUES (:s,:u,:inv,:cu,:uid,:dev,:sub,:disc,:tax,:tot,:paid,:chg,:pm,NOW())',
                ['s' => $ctx['shop_id'], 'u' => $in['uuid'], 'inv' => $in['invoice_no'],
                 'cu' => $in['customer_uuid'], 'uid' => $ctx['user_id'], 'dev' => $ctx['device'],
                 'sub' => $in['subtotal'], 'disc' => $in['discount'], 'tax' => $in['tax'],
                 'tot' => $in['total'], 'paid' => $in['paid'], 'chg' => $in['change_due'], 'pm' => $in['payment_method']]
            );
            $orderId = Database::insertId();
            foreach ($in['items'] as $item) {
                Database::run(
                    'INSERT INTO order_items (order_id, shop_id, product_uuid, name, qty, unit_price, discount, tax_percent, line_total)
                     VALUES (:o,:s,:p,:n,:q,:up,:d,:tp,:lt)',
                    ['o' => $orderId, 's' => $ctx['shop_id'], 'p' => $item['product_uuid'] ?? null,
                     'n' => (string) ($item['name'] ?? 'Item'), 'q' => (float) ($item['qty'] ?? 1),
                     'up' => (float) ($item['unit_price'] ?? 0), 'd' => (float) ($item['discount'] ?? 0),
                     'tp' => (float) ($item['tax_percent'] ?? 0), 'lt' => (float) ($item['line_total'] ?? 0)]
                );
                if (!empty($item['product_uuid'])) {
                    Database::run('UPDATE products SET stock = stock - :q, updated_at = NOW()
                                    WHERE shop_id = :s AND uuid = :u AND track_stock = 1',
                        ['q' => (float) ($item['qty'] ?? 1), 's' => $ctx['shop_id'], 'u' => $item['product_uuid']]);
                }
            }
        });

        Response::ok(['uuid' => $in['uuid']], 'Order recorded');
    }

    // ---------------- expenses ----------------
    public function expenses(Request $req): void
    {
        $ctx = Auth::requireUser($req);
        Response::ok(Database::all(
            'SELECT * FROM expenses WHERE shop_id = :s AND deleted_at IS NULL ORDER BY spent_at DESC LIMIT 500',
            ['s' => $ctx['shop_id']]
        ));
    }

    public function saveExpense(Request $req): void
    {
        $ctx = Auth::requireUser($req);
        $in  = (new Validator($req->body))
            ->uuid('uuid')->required('title', 190)->optional('category', 80)
            ->number('amount', 0, 1e9)->optional('note', 400)->optional('spent_at', 30)
            ->validOrFail();
        Database::run(
            'INSERT INTO expenses (shop_id, uuid, title, category, amount, note, spent_at)
             VALUES (:s,:u,:t,:c,:a,:n,COALESCE(:sp, NOW()))
             ON DUPLICATE KEY UPDATE title=VALUES(title), category=VALUES(category), amount=VALUES(amount),
                 note=VALUES(note), spent_at=VALUES(spent_at), updated_at=NOW(), deleted_at=NULL',
            ['s' => $ctx['shop_id'], 'u' => $in['uuid'], 't' => $in['title'], 'c' => $in['category'],
             'a' => $in['amount'], 'n' => $in['note'],
             'sp' => $in['spent_at'] ? date('Y-m-d H:i:s', (int) strtotime($in['spent_at'])) : null]
        );
        Response::ok(['uuid' => $in['uuid']], 'Expense saved');
    }
}
