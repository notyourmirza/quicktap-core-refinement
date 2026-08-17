<?php
declare(strict_types=1);

namespace QuickTap\Controllers;

use QuickTap\Core\{Auth, Database, Request, Response};

/** Sales / profit / product / expense analytics. */
final class ReportController
{
    public function summary(Request $req): void
    {
        $ctx  = Auth::requireUser($req);
        $from = (string) $req->input('from', date('Y-m-01'));
        $to   = (string) $req->input('to', date('Y-m-d'));
        $p    = ['s' => $ctx['shop_id'], 'f' => $from, 't' => $to];

        $sales = Database::first(
            'SELECT COUNT(*) AS orders, COALESCE(SUM(total),0) AS revenue,
                    COALESCE(SUM(discount),0) AS discount, COALESCE(SUM(tax),0) AS tax,
                    COALESCE(AVG(total),0) AS avg_ticket
               FROM orders
              WHERE shop_id = :s AND deleted_at IS NULL AND status = "completed"
                AND DATE(ordered_at) BETWEEN :f AND :t', $p);

        $cogs = Database::first(
            'SELECT COALESCE(SUM(oi.qty * p.cost_price),0) AS cogs
               FROM order_items oi
               JOIN orders o ON o.id = oi.order_id
          LEFT JOIN products p ON p.shop_id = oi.shop_id AND p.uuid = oi.product_uuid
              WHERE o.shop_id = :s AND o.deleted_at IS NULL AND o.status = "completed"
                AND DATE(o.ordered_at) BETWEEN :f AND :t', $p);

        $expense = Database::first(
            'SELECT COALESCE(SUM(amount),0) AS expenses FROM expenses
              WHERE shop_id = :s AND deleted_at IS NULL AND DATE(spent_at) BETWEEN :f AND :t', $p);

        $revenue = (float) $sales['revenue'];
        $profit  = $revenue - (float) $cogs['cogs'] - (float) $expense['expenses'];

        Response::ok([
            'range'       => ['from' => $from, 'to' => $to],
            'orders'      => (int) $sales['orders'],
            'revenue'     => round($revenue, 2),
            'discount'    => round((float) $sales['discount'], 2),
            'tax'         => round((float) $sales['tax'], 2),
            'avg_ticket'  => round((float) $sales['avg_ticket'], 2),
            'cogs'        => round((float) $cogs['cogs'], 2),
            'expenses'    => round((float) $expense['expenses'], 2),
            'net_profit'  => round($profit, 2),
        ]);
    }

    public function daily(Request $req): void
    {
        $ctx  = Auth::requireUser($req);
        $days = min(180, max(1, (int) $req->input('days', 30)));
        Response::ok(Database::all(
            'SELECT DATE(ordered_at) AS day, COUNT(*) AS orders, COALESCE(SUM(total),0) AS revenue
               FROM orders
              WHERE shop_id = :s AND deleted_at IS NULL AND status = "completed"
                AND ordered_at >= DATE_SUB(CURDATE(), INTERVAL :d DAY)
              GROUP BY DATE(ordered_at) ORDER BY day ASC',
            ['s' => $ctx['shop_id'], 'd' => $days]
        ));
    }

    public function topProducts(Request $req): void
    {
        $ctx   = Auth::requireUser($req);
        $limit = min(100, max(1, (int) $req->input('limit', 20)));
        Response::ok(Database::all(
            'SELECT oi.name, oi.product_uuid, SUM(oi.qty) AS qty, SUM(oi.line_total) AS revenue
               FROM order_items oi JOIN orders o ON o.id = oi.order_id
              WHERE o.shop_id = :s AND o.status = "completed" AND o.deleted_at IS NULL
              GROUP BY oi.product_uuid, oi.name
              ORDER BY revenue DESC LIMIT ' . $limit,
            ['s' => $ctx['shop_id']]
        ));
    }

    public function lowStock(Request $req): void
    {
        $ctx = Auth::requireUser($req);
        $threshold = (float) $req->input('threshold', 5);
        Response::ok(Database::all(
            'SELECT uuid, name, stock, price FROM products
              WHERE shop_id = :s AND deleted_at IS NULL AND track_stock = 1 AND stock <= :th
              ORDER BY stock ASC LIMIT 200',
            ['s' => $ctx['shop_id'], 'th' => $threshold]
        ));
    }
}
