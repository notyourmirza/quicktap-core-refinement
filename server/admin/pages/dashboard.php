<?php
/** Dashboard — platform-wide KPIs, revenue trend and live activity. */

use QuickTap\Core\Database;

$totalShops   = (int) (Database::first('SELECT COUNT(*) c FROM shops WHERE deleted_at IS NULL')['c'] ?? 0);
$activeShops  = (int) (Database::first('SELECT COUNT(*) c FROM shops WHERE deleted_at IS NULL AND status = "active"')['c'] ?? 0);
$totalUsers   = (int) (Database::first('SELECT COUNT(*) c FROM users WHERE deleted_at IS NULL')['c'] ?? 0);
$totalDevices = (int) (Database::first('SELECT COUNT(*) c FROM devices WHERE status = "active"')['c'] ?? 0);

$today = Database::first(
    'SELECT COALESCE(SUM(total),0) revenue, COUNT(*) orders
       FROM orders
      WHERE deleted_at IS NULL AND status = "completed" AND DATE(ordered_at) = CURDATE()'
) ?: ['revenue' => 0, 'orders' => 0];

$month = Database::first(
    'SELECT COALESCE(SUM(total),0) revenue, COUNT(*) orders
       FROM orders
      WHERE deleted_at IS NULL AND status = "completed"
        AND ordered_at >= DATE_FORMAT(CURDATE(), "%Y-%m-01")'
) ?: ['revenue' => 0, 'orders' => 0];

$expiring = Database::all(
    'SELECT id, name, status, subscription_ends_at
       FROM shops
      WHERE deleted_at IS NULL AND subscription_ends_at IS NOT NULL
        AND subscription_ends_at BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 14 DAY)
      ORDER BY subscription_ends_at ASC LIMIT 8'
);

$trend = Database::all(
    'SELECT DATE(ordered_at) d, COALESCE(SUM(total),0) revenue
       FROM orders
      WHERE deleted_at IS NULL AND status = "completed"
        AND ordered_at >= DATE_SUB(CURDATE(), INTERVAL 29 DAY)
      GROUP BY DATE(ordered_at) ORDER BY d ASC'
);
$byDay = [];
foreach ($trend as $t) {
    $byDay[(string) $t['d']] = (float) $t['revenue'];
}
$labels = $values = [];
for ($i = 29; $i >= 0; $i--) {
    $day      = date('Y-m-d', strtotime("-$i day"));
    $labels[] = date('d M', strtotime($day));
    $values[] = $byDay[$day] ?? 0;
}

$topShops = Database::all(
    'SELECT s.id, s.name, COALESCE(SUM(o.total),0) revenue, COUNT(o.id) orders
       FROM shops s
       LEFT JOIN orders o ON o.shop_id = s.id AND o.deleted_at IS NULL AND o.status = "completed"
        AND o.ordered_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
      WHERE s.deleted_at IS NULL
      GROUP BY s.id, s.name ORDER BY revenue DESC LIMIT 8'
);

$recent = Database::all(
    'SELECT l.action, l.entity, l.created_at, l.actor_type, s.name shop_name
       FROM activity_logs l LEFT JOIN shops s ON s.id = l.shop_id
      ORDER BY l.id DESC LIMIT 10'
);

$stats = [
    ['Total shops',    number_format($totalShops),        $activeShops . ' active'],
    ['Bound devices',  number_format($totalDevices),      'across all tenants'],
    ['Staff accounts', number_format($totalUsers),        'owners, managers, cashiers'],
    ['Revenue today',  money($today['revenue']),          (int) $today['orders'] . ' orders'],
    ['Revenue MTD',    money($month['revenue']),          (int) $month['orders'] . ' orders'],
];
?>

<div class="row g-3 mb-4">
  <?php foreach ($stats as [$label, $value, $trendText]): ?>
    <div class="col-6 col-lg">
      <div class="stat-card">
        <div class="label"><?= e($label) ?></div>
        <div class="value"><?= e($value) ?></div>
        <div class="trend"><?= e($trendText) ?></div>
      </div>
    </div>
  <?php endforeach; ?>
</div>

<div class="row g-3">
  <div class="col-lg-8">
    <div class="card h-100">
      <div class="card-header">Revenue — last 30 days</div>
      <div class="card-body">
        <div style="height:300px">
          <canvas id="revenueChart"
                  data-labels='<?= e(json_encode($labels)) ?>'
                  data-values='<?= e(json_encode($values)) ?>'></canvas>
        </div>
      </div>
    </div>
  </div>

  <div class="col-lg-4">
    <div class="card h-100">
      <div class="card-header">Subscriptions expiring soon</div>
      <div class="list-group list-group-flush">
        <?php if (!$expiring): ?>
          <div class="empty">Nothing expiring in the next 14 days.</div>
        <?php endif; ?>
        <?php foreach ($expiring as $s): ?>
          <a class="list-group-item bg-transparent border-0 d-flex justify-content-between align-items-center py-2 px-3"
             href="<?= e(url('shop', ['id' => (int) $s['id']])) ?>">
            <span><?= e($s['name']) ?></span>
            <span class="small text-warning"><?= e(nice_date($s['subscription_ends_at'], 'd M Y')) ?></span>
          </a>
        <?php endforeach; ?>
      </div>
    </div>
  </div>

  <div class="col-lg-7">
    <div class="card">
      <div class="card-header">Top shops — last 30 days</div>
      <div class="table-responsive">
        <table class="table align-middle">
          <thead><tr><th>Shop</th><th class="text-end">Orders</th><th class="text-end">Revenue</th></tr></thead>
          <tbody>
          <?php foreach ($topShops as $s): ?>
            <tr>
              <td><a href="<?= e(url('shop', ['id' => (int) $s['id']])) ?>" class="link-light"><?= e($s['name']) ?></a></td>
              <td class="text-end"><?= (int) $s['orders'] ?></td>
              <td class="text-end fw-semibold"><?= e(money($s['revenue'])) ?></td>
            </tr>
          <?php endforeach; ?>
          <?php if (!$topShops): ?><tr><td colspan="3" class="empty">No shops yet.</td></tr><?php endif; ?>
          </tbody>
        </table>
      </div>
    </div>
  </div>

  <div class="col-lg-5">
    <div class="card">
      <div class="card-header">Recent activity</div>
      <div class="table-responsive">
        <table class="table align-middle">
          <tbody>
          <?php foreach ($recent as $r): ?>
            <tr>
              <td>
                <div class="fw-semibold small"><?= e($r['action']) ?></div>
                <div class="text-secondary small"><?= e($r['shop_name'] ?? 'Platform') ?> · <?= e($r['actor_type']) ?></div>
              </td>
              <td class="text-end text-secondary small"><?= e(nice_date($r['created_at'], 'd M H:i')) ?></td>
            </tr>
          <?php endforeach; ?>
          <?php if (!$recent): ?><tr><td class="empty">No activity recorded.</td></tr><?php endif; ?>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</div>
