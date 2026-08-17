<?php
/** Platform-wide reports with CSV export. */

use QuickTap\Core\Database;

$from   = query('from') !== '' ? query('from') : date('Y-m-d', strtotime('-29 days'));
$to     = query('to')   !== '' ? query('to')   : date('Y-m-d');
$shopId = query_int('shop_id');

if (!preg_match('/^\d{4}-\d{2}-\d{2}$/', $from)) { $from = date('Y-m-d', strtotime('-29 days')); }
if (!preg_match('/^\d{4}-\d{2}-\d{2}$/', $to))   { $to   = date('Y-m-d'); }

$scope  = 'o.deleted_at IS NULL AND o.status = "completed" AND DATE(o.ordered_at) BETWEEN ? AND ?';
$params = [$from, $to];
if ($shopId > 0) { $scope .= ' AND o.shop_id = ?'; $params[] = $shopId; }

$summary = Database::first(
    "SELECT COALESCE(SUM(o.total),0) revenue, COALESCE(SUM(o.discount),0) discount,
            COALESCE(SUM(o.tax),0) tax, COUNT(*) orders
       FROM orders o WHERE $scope",
    $params
) ?: ['revenue' => 0, 'discount' => 0, 'tax' => 0, 'orders' => 0];

$profit = Database::first(
    "SELECT COALESCE(SUM(oi.line_total - (p.cost_price * oi.qty)),0) profit
       FROM order_items oi
       JOIN orders o ON o.id = oi.order_id
       LEFT JOIN products p ON p.shop_id = oi.shop_id AND p.uuid = oi.product_uuid
      WHERE $scope",
    $params
)['profit'] ?? 0;

$expenseScope  = 'e.deleted_at IS NULL AND DATE(e.spent_at) BETWEEN ? AND ?';
$expenseParams = [$from, $to];
if ($shopId > 0) { $expenseScope .= ' AND e.shop_id = ?'; $expenseParams[] = $shopId; }
$expenses = (float) (Database::first("SELECT COALESCE(SUM(e.amount),0) total FROM expenses e WHERE $expenseScope", $expenseParams)['total'] ?? 0);

$daily = Database::all(
    "SELECT DATE(o.ordered_at) d, COALESCE(SUM(o.total),0) revenue, COUNT(*) orders
       FROM orders o WHERE $scope GROUP BY DATE(o.ordered_at) ORDER BY d ASC",
    $params
);

$byShop = Database::all(
    "SELECT s.id, s.name, COALESCE(SUM(o.total),0) revenue, COUNT(o.id) orders
       FROM orders o JOIN shops s ON s.id = o.shop_id
      WHERE $scope GROUP BY s.id, s.name ORDER BY revenue DESC LIMIT 20",
    $params
);

$topProducts = Database::all(
    "SELECT oi.name, SUM(oi.qty) qty, SUM(oi.line_total) revenue
       FROM order_items oi JOIN orders o ON o.id = oi.order_id
      WHERE $scope GROUP BY oi.name ORDER BY revenue DESC LIMIT 15",
    $params
);

$payments = Database::all(
    "SELECT o.payment_method m, COUNT(*) c, COALESCE(SUM(o.total),0) revenue
       FROM orders o WHERE $scope GROUP BY o.payment_method ORDER BY revenue DESC",
    $params
);

if (query('export') === 'csv') {
    while (ob_get_level() > 0) { ob_end_clean(); }
    header('Content-Type: text/csv; charset=utf-8');
    header('Content-Disposition: attachment; filename="quicktap-report-' . $from . '_' . $to . '.csv"');
    $out = fopen('php://output', 'w');
    fputcsv($out, ['Date', 'Orders', 'Revenue']);
    foreach ($daily as $d) { fputcsv($out, [$d['d'], (int) $d['orders'], (float) $d['revenue']]); }
    fputcsv($out, []);
    fputcsv($out, ['Shop', 'Orders', 'Revenue']);
    foreach ($byShop as $s) { fputcsv($out, [$s['name'], (int) $s['orders'], (float) $s['revenue']]); }
    fclose($out);
    exit;
}

$labels = array_map(fn($d) => date('d M', strtotime((string) $d['d'])), $daily);
$values = array_map(fn($d) => (float) $d['revenue'], $daily);
$shops  = Database::all('SELECT id, name FROM shops WHERE deleted_at IS NULL ORDER BY name');
$net    = (float) $profit - $expenses;
?>

<form class="d-flex flex-wrap gap-2 mb-3" method="get">
  <input type="hidden" name="p" value="reports">
  <input type="date" name="from" class="form-control form-control-sm" style="width:170px" value="<?= e($from) ?>">
  <input type="date" name="to" class="form-control form-control-sm" style="width:170px" value="<?= e($to) ?>">
  <select name="shop_id" class="form-select form-select-sm" style="width:220px">
    <option value="">All shops</option>
    <?php foreach ($shops as $s): ?>
      <option value="<?= (int) $s['id'] ?>" <?= $shopId === (int) $s['id'] ? 'selected' : '' ?>><?= e($s['name']) ?></option>
    <?php endforeach; ?>
  </select>
  <button class="btn btn-sm btn-primary">Apply</button>
  <a class="btn btn-sm btn-outline-secondary ms-auto"
     href="<?= e(url('reports', array_filter(['from' => $from, 'to' => $to, 'shop_id' => $shopId ?: '', 'export' => 'csv']))) ?>">
    <i class="bi bi-download"></i> Export CSV
  </a>
</form>

<div class="row g-3 mb-3">
  <div class="col-6 col-lg"><div class="stat-card"><div class="label">Revenue</div><div class="value"><?= e(money($summary['revenue'])) ?></div><div class="trend"><?= (int) $summary['orders'] ?> orders</div></div></div>
  <div class="col-6 col-lg"><div class="stat-card"><div class="label">Gross profit</div><div class="value"><?= e(money($profit)) ?></div><div class="trend">revenue − cost</div></div></div>
  <div class="col-6 col-lg"><div class="stat-card"><div class="label">Expenses</div><div class="value"><?= e(money($expenses)) ?></div><div class="trend">recorded</div></div></div>
  <div class="col-6 col-lg"><div class="stat-card"><div class="label">Net</div><div class="value <?= $net < 0 ? 'text-danger' : 'text-success' ?>"><?= e(money($net)) ?></div><div class="trend">profit − expenses</div></div></div>
  <div class="col-6 col-lg"><div class="stat-card"><div class="label">Discounts / tax</div><div class="value" style="font-size:1.2rem"><?= e(money($summary['discount'])) ?> / <?= e(money($summary['tax'])) ?></div><div class="trend">in period</div></div></div>
</div>

<div class="card mb-3">
  <div class="card-header">Revenue trend</div>
  <div class="card-body"><div style="height:280px">
    <canvas id="revenueChart" data-labels='<?= e(json_encode($labels)) ?>' data-values='<?= e(json_encode($values)) ?>'></canvas>
  </div></div>
</div>

<div class="row g-3">
  <div class="col-lg-5">
    <div class="card h-100">
      <div class="card-header">Revenue by shop</div>
      <div class="table-responsive">
        <table class="table align-middle">
          <thead><tr><th>Shop</th><th class="text-end">Orders</th><th class="text-end">Revenue</th></tr></thead>
          <tbody>
          <?php foreach ($byShop as $s): ?>
            <tr><td><a class="link-light" href="<?= e(url('shop', ['id' => (int) $s['id']])) ?>"><?= e($s['name']) ?></a></td>
              <td class="text-end"><?= (int) $s['orders'] ?></td><td class="text-end fw-semibold"><?= e(money($s['revenue'])) ?></td></tr>
          <?php endforeach; ?>
          <?php if (!$byShop): ?><tr><td colspan="3" class="empty">No sales in this period.</td></tr><?php endif; ?>
          </tbody>
        </table>
      </div>
    </div>
  </div>

  <div class="col-lg-4">
    <div class="card h-100">
      <div class="card-header">Top products</div>
      <div class="table-responsive">
        <table class="table align-middle">
          <thead><tr><th>Product</th><th class="text-end">Qty</th><th class="text-end">Revenue</th></tr></thead>
          <tbody>
          <?php foreach ($topProducts as $p): ?>
            <tr><td><?= e($p['name']) ?></td>
              <td class="text-end"><?= rtrim(rtrim(number_format((float) $p['qty'], 2), '0'), '.') ?></td>
              <td class="text-end"><?= e(money($p['revenue'])) ?></td></tr>
          <?php endforeach; ?>
          <?php if (!$topProducts): ?><tr><td colspan="3" class="empty">No product sales.</td></tr><?php endif; ?>
          </tbody>
        </table>
      </div>
    </div>
  </div>

  <div class="col-lg-3">
    <div class="card h-100">
      <div class="card-header">Payment mix</div>
      <div class="table-responsive">
        <table class="table align-middle">
          <tbody>
          <?php foreach ($payments as $pm): ?>
            <tr><td class="text-capitalize"><?= e($pm['m']) ?><div class="text-secondary small"><?= (int) $pm['c'] ?> orders</div></td>
              <td class="text-end fw-semibold"><?= e(money($pm['revenue'])) ?></td></tr>
          <?php endforeach; ?>
          <?php if (!$payments): ?><tr><td class="empty">No payments.</td></tr><?php endif; ?>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</div>
