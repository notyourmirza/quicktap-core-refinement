<?php
/** Audit trail across the platform. */

use QuickTap\Core\Database;

$search = query('q');
$actor  = pick(query('actor'), ['user', 'admin', 'system'], '');
$shopId = query_int('shop_id');

$where  = ['1=1'];
$params = [];
if ($search !== '') {
    $where[]  = '(l.action LIKE ? OR l.entity LIKE ? OR l.ip LIKE ?)';
    $like     = '%' . $search . '%';
    $params[] = $like; $params[] = $like; $params[] = $like;
}
if ($actor !== '') { $where[] = 'l.actor_type = ?'; $params[] = $actor; }
if ($shopId > 0)   { $where[] = 'l.shop_id = ?';    $params[] = $shopId; }
$whereSql = 'WHERE ' . implode(' AND ', $where);

$total = (int) (Database::first("SELECT COUNT(*) c FROM activity_logs l $whereSql", $params)['c'] ?? 0);
$pg    = paginate($total, 30);
$rows  = Database::all(
    "SELECT l.*, s.name shop_name FROM activity_logs l LEFT JOIN shops s ON s.id = l.shop_id
     $whereSql ORDER BY l.id DESC LIMIT {$pg['per']} OFFSET {$pg['offset']}",
    $params
);
$shops = Database::all('SELECT id, name FROM shops WHERE deleted_at IS NULL ORDER BY name');
?>

<form class="d-flex flex-wrap gap-2 mb-3" method="get">
  <input type="hidden" name="p" value="logs">
  <input name="q" class="form-control form-control-sm" style="width:240px" placeholder="Search action, entity, IP…" value="<?= e($search) ?>">
  <select name="actor" class="form-select form-select-sm" style="width:150px">
    <option value="">All actors</option>
    <?php foreach (['user', 'admin', 'system'] as $a): ?>
      <option value="<?= $a ?>" <?= $actor === $a ? 'selected' : '' ?>><?= ucfirst($a) ?></option>
    <?php endforeach; ?>
  </select>
  <select name="shop_id" class="form-select form-select-sm" style="width:220px">
    <option value="">All shops</option>
    <?php foreach ($shops as $s): ?>
      <option value="<?= (int) $s['id'] ?>" <?= $shopId === (int) $s['id'] ? 'selected' : '' ?>><?= e($s['name']) ?></option>
    <?php endforeach; ?>
  </select>
  <button class="btn btn-sm btn-outline-secondary">Filter</button>
</form>

<div class="card">
  <div class="table-responsive">
    <table class="table align-middle">
      <thead><tr><th>Action</th><th>Shop</th><th>Actor</th><th>Entity</th><th>IP</th><th>When</th></tr></thead>
      <tbody>
      <?php foreach ($rows as $l): ?>
        <tr>
          <td class="fw-semibold"><?= e($l['action']) ?></td>
          <td class="small"><?= e($l['shop_name'] ?: 'Platform') ?></td>
          <td class="small text-capitalize"><?= e($l['actor_type']) ?> #<?= e($l['actor_id'] ?: '—') ?></td>
          <td class="small"><?= e($l['entity'] ?: '—') ?><?= $l['entity_id'] ? ' #' . e($l['entity_id']) : '' ?></td>
          <td class="small font-monospace"><?= e($l['ip'] ?: '—') ?></td>
          <td class="small"><?= e(nice_date($l['created_at'])) ?></td>
        </tr>
      <?php endforeach; ?>
      <?php if (!$rows): ?><tr><td colspan="6" class="empty">No log entries.</td></tr><?php endif; ?>
      </tbody>
    </table>
  </div>
  <div class="d-flex justify-content-between align-items-center p-3">
    <span class="small text-secondary"><?= number_format($total) ?> entries</span>
    <?= pager($pg, 'logs', array_filter(['q' => $search, 'actor' => $actor, 'shop_id' => $shopId ?: ''])) ?>
  </div>
</div>
