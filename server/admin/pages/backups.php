<?php
/** Backup registry across all shops (Google Drive metadata pushed by devices). */

use QuickTap\Core\Database;

$shopId = query_int('shop_id');
$kind   = pick(query('kind'), ['auto', 'manual'], '');

$where  = ['1=1'];
$params = [];
if ($shopId > 0) { $where[] = 'b.shop_id = ?'; $params[] = $shopId; }
if ($kind !== '') { $where[] = 'b.kind = ?';   $params[] = $kind; }
$whereSql = 'WHERE ' . implode(' AND ', $where);

$total = (int) (Database::first("SELECT COUNT(*) c FROM backups b $whereSql", $params)['c'] ?? 0);
$pg    = paginate($total, 25);
$rows  = Database::all(
    "SELECT b.*, s.name shop_name, u.username FROM backups b
       LEFT JOIN shops s ON s.id = b.shop_id LEFT JOIN users u ON u.id = b.user_id
     $whereSql ORDER BY b.created_at DESC LIMIT {$pg['per']} OFFSET {$pg['offset']}",
    $params
);
$stats = Database::first('SELECT COUNT(*) c, COALESCE(SUM(size_bytes),0) bytes FROM backups') ?: ['c' => 0, 'bytes' => 0];
$stale = Database::all(
    'SELECT s.id, s.name, MAX(b.created_at) last_backup
       FROM shops s LEFT JOIN backups b ON b.shop_id = s.id
      WHERE s.deleted_at IS NULL
      GROUP BY s.id, s.name
     HAVING last_backup IS NULL OR last_backup < DATE_SUB(NOW(), INTERVAL 3 DAY)
      ORDER BY last_backup IS NOT NULL, last_backup ASC LIMIT 10'
);
$shops = Database::all('SELECT id, name FROM shops WHERE deleted_at IS NULL ORDER BY name');
?>

<div class="row g-3 mb-3">
  <div class="col-6 col-lg-3"><div class="stat-card"><div class="label">Backups stored</div><div class="value"><?= number_format((int) $stats['c']) ?></div><div class="trend">all shops</div></div></div>
  <div class="col-6 col-lg-3"><div class="stat-card"><div class="label">Total size</div><div class="value"><?= number_format(((int) $stats['bytes']) / 1048576, 1) ?> MB</div><div class="trend">on Google Drive</div></div></div>
  <div class="col-12 col-lg-6"><div class="stat-card"><div class="label">Shops without a recent backup</div><div class="value"><?= count($stale) ?></div><div class="trend">no backup in the last 3 days</div></div></div>
</div>

<form class="d-flex flex-wrap gap-2 mb-3" method="get">
  <input type="hidden" name="p" value="backups">
  <select name="shop_id" class="form-select form-select-sm" style="width:230px">
    <option value="">All shops</option>
    <?php foreach ($shops as $s): ?>
      <option value="<?= (int) $s['id'] ?>" <?= $shopId === (int) $s['id'] ? 'selected' : '' ?>><?= e($s['name']) ?></option>
    <?php endforeach; ?>
  </select>
  <select name="kind" class="form-select form-select-sm" style="width:150px">
    <option value="">Any kind</option>
    <option value="auto" <?= $kind === 'auto' ? 'selected' : '' ?>>Automatic</option>
    <option value="manual" <?= $kind === 'manual' ? 'selected' : '' ?>>Manual</option>
  </select>
  <button class="btn btn-sm btn-outline-secondary">Filter</button>
</form>

<div class="row g-3">
  <div class="col-lg-8">
    <div class="card">
      <div class="table-responsive">
        <table class="table align-middle">
          <thead><tr><th>File</th><th>Shop</th><th>By</th><th>Kind</th><th>Size</th><th>Created</th></tr></thead>
          <tbody>
          <?php foreach ($rows as $b): ?>
            <tr>
              <td><span class="fw-semibold"><?= e($b['file_name']) ?></span>
                <div class="text-secondary small">
                  <?= (int) $b['encrypted'] ? '<i class="bi bi-shield-lock"></i> encrypted' : 'plain' ?>
                  · <?= e($b['checksum'] ? substr((string) $b['checksum'], 0, 12) : 'no checksum') ?>
                </div></td>
              <td class="small"><a class="link-light" href="<?= e(url('shop', ['id' => (int) $b['shop_id'], 'tab' => 'backups'])) ?>"><?= e($b['shop_name'] ?: '—') ?></a></td>
              <td class="small"><?= e($b['username'] ?: '—') ?></td>
              <td class="text-capitalize small"><?= e($b['kind']) ?></td>
              <td class="small"><?= number_format(((int) $b['size_bytes']) / 1024, 1) ?> KB</td>
              <td class="small"><?= e(nice_date($b['created_at'])) ?></td>
            </tr>
          <?php endforeach; ?>
          <?php if (!$rows): ?><tr><td colspan="6" class="empty">No backups registered.</td></tr><?php endif; ?>
          </tbody>
        </table>
      </div>
      <div class="d-flex justify-content-between align-items-center p-3">
        <span class="small text-secondary"><?= number_format($total) ?> records</span>
        <?= pager($pg, 'backups', array_filter(['shop_id' => $shopId ?: '', 'kind' => $kind])) ?>
      </div>
    </div>
  </div>

  <div class="col-lg-4">
    <div class="card h-100">
      <div class="card-header">Backup health</div>
      <div class="table-responsive">
        <table class="table align-middle">
          <tbody>
          <?php foreach ($stale as $s): ?>
            <tr>
              <td><a class="link-light" href="<?= e(url('shop', ['id' => (int) $s['id'], 'tab' => 'backups'])) ?>"><?= e($s['name']) ?></a></td>
              <td class="text-end small <?= $s['last_backup'] ? 'text-warning' : 'text-danger' ?>">
                <?= $s['last_backup'] ? e(nice_date($s['last_backup'], 'd M Y')) : 'Never' ?>
              </td>
            </tr>
          <?php endforeach; ?>
          <?php if (!$stale): ?><tr><td class="empty">Every shop backed up recently.</td></tr><?php endif; ?>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</div>
