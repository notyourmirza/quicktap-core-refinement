<?php
/** Global device registry across every tenant. */

use Admin\AdminLog;
use Admin\Csrf;
use Admin\Flash;
use QuickTap\Core\Database;

if (is_post()) {
    $id     = post_int('device_id');
    $shopId = post_int('shop_id');
    $action = post('action');

    if ($action === 'reset' && $id > 0) {
        Database::transaction(function () use ($id, $shopId) {
            $device = Database::first('SELECT user_id FROM devices WHERE id = ?', [$id]);
            if ($device && $device['user_id']) {
                Database::run('UPDATE users SET device_id = NULL, device_name = NULL, device_bound_at = NULL WHERE id = ?', [(int) $device['user_id']]);
                Database::run('UPDATE refresh_tokens SET revoked_at = NOW() WHERE user_id = ? AND revoked_at IS NULL', [(int) $device['user_id']]);
            }
            Database::run('UPDATE devices SET status = "reset", user_id = NULL WHERE id = ?', [$id]);
        });
        AdminLog::write('device_reset', 'device', (string) $id, $shopId ?: null);
        Flash::success('Device unbound.');
    } elseif ($action === 'toggle' && $id > 0) {
        $to = pick(post('status'), ['active', 'blocked'], 'blocked');
        Database::run('UPDATE devices SET status = ? WHERE id = ?', [$to, $id]);
        AdminLog::write('device_' . $to, 'device', (string) $id, $shopId ?: null);
        Flash::success('Device marked as ' . $to . '.');
    }
    redirect(url('devices', array_filter(['q' => query('q'), 'status' => query('status')])));
}

$search = query('q');
$status = pick(query('status'), ['active', 'reset', 'blocked'], '');

$where  = ['1=1'];
$params = [];
if ($search !== '') {
    $where[]  = '(d.device_name LIKE ? OR s.name LIKE ? OR u.username LIKE ?)';
    $like     = '%' . $search . '%';
    $params[] = $like; $params[] = $like; $params[] = $like;
}
if ($status !== '') { $where[] = 'd.status = ?'; $params[] = $status; }
$whereSql = 'WHERE ' . implode(' AND ', $where);

$total = (int) (Database::first(
    "SELECT COUNT(*) c FROM devices d LEFT JOIN shops s ON s.id = d.shop_id LEFT JOIN users u ON u.id = d.user_id $whereSql",
    $params
)['c'] ?? 0);
$pg = paginate($total, 20);

$rows = Database::all(
    "SELECT d.*, s.name shop_name, u.username
       FROM devices d LEFT JOIN shops s ON s.id = d.shop_id LEFT JOIN users u ON u.id = d.user_id
       $whereSql ORDER BY d.last_seen_at DESC, d.id DESC LIMIT {$pg['per']} OFFSET {$pg['offset']}",
    $params
);
?>

<form class="d-flex gap-2 flex-wrap mb-3" method="get">
  <input type="hidden" name="p" value="devices">
  <input name="q" class="form-control form-control-sm" style="width:260px" placeholder="Search device, shop or user…" value="<?= e($search) ?>">
  <select name="status" class="form-select form-select-sm" style="width:150px">
    <option value="">All statuses</option>
    <?php foreach (['active', 'reset', 'blocked'] as $s): ?>
      <option value="<?= $s ?>" <?= $status === $s ? 'selected' : '' ?>><?= ucfirst($s) ?></option>
    <?php endforeach; ?>
  </select>
  <button class="btn btn-sm btn-outline-secondary">Filter</button>
</form>

<div class="card">
  <div class="table-responsive">
    <table class="table align-middle">
      <thead><tr><th>Device</th><th>Shop</th><th>User</th><th>App</th><th>Last seen</th><th>Status</th><th></th></tr></thead>
      <tbody>
      <?php foreach ($rows as $d): ?>
        <tr>
          <td><span class="fw-semibold"><?= e($d['device_name'] ?: 'Unnamed device') ?></span>
            <div class="text-secondary small font-monospace"><?= e(substr((string) $d['device_id'], 0, 18)) ?>…</div></td>
          <td><a class="link-light" href="<?= e(url('shop', ['id' => (int) $d['shop_id']])) ?>"><?= e($d['shop_name'] ?: '—') ?></a></td>
          <td><?= e($d['username'] ?: '—') ?></td>
          <td class="small"><?= e($d['app_version'] ?: '—') ?> / <?= e($d['os_version'] ?: '—') ?></td>
          <td class="small"><?= e(nice_date($d['last_seen_at'])) ?></td>
          <td><?= status_badge((string) $d['status']) ?></td>
          <td class="text-end">
            <form method="post" class="d-inline" data-confirm="Unbind this device?">
              <?= Csrf::field() ?><input type="hidden" name="action" value="reset">
              <input type="hidden" name="device_id" value="<?= (int) $d['id'] ?>"><input type="hidden" name="shop_id" value="<?= (int) $d['shop_id'] ?>">
              <button class="btn btn-sm btn-outline-secondary">Reset</button>
            </form>
            <form method="post" class="d-inline">
              <?= Csrf::field() ?><input type="hidden" name="action" value="toggle">
              <input type="hidden" name="device_id" value="<?= (int) $d['id'] ?>"><input type="hidden" name="shop_id" value="<?= (int) $d['shop_id'] ?>">
              <input type="hidden" name="status" value="<?= $d['status'] === 'blocked' ? 'active' : 'blocked' ?>">
              <button class="btn btn-sm btn-outline-<?= $d['status'] === 'blocked' ? 'success' : 'danger' ?>"><?= $d['status'] === 'blocked' ? 'Unblock' : 'Block' ?></button>
            </form>
          </td>
        </tr>
      <?php endforeach; ?>
      <?php if (!$rows): ?><tr><td colspan="7" class="empty">No devices found.</td></tr><?php endif; ?>
      </tbody>
    </table>
  </div>
  <div class="d-flex justify-content-between align-items-center p-3">
    <span class="small text-secondary"><?= $total ?> devices</span>
    <?= pager($pg, 'devices', array_filter(['q' => $search, 'status' => $status])) ?>
  </div>
</div>
