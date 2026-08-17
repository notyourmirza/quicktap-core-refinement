<?php
/** Buy-now requests submitted from the app marketplace. */

use Admin\AdminLog;
use Admin\Csrf;
use Admin\Flash;
use QuickTap\Core\Database;

if (!table_exists('market_requests')) {
    echo missing_table_notice('market_requests', '2026_08_10_marketplace.sql');
    return;
}

if (is_post()) {
    if (post('action') === 'status') {
        $id     = post_int('id');
        $status = pick(post('status'), ['new', 'contacted', 'approved', 'rejected', 'completed'], 'new');
        try {
            Database::run('UPDATE market_requests SET status = ? WHERE id = ?', [$status, $id]);
            AdminLog::write('market_request_' . $status, 'market_request', (string) $id);
            Flash::success('Request updated.');
        } catch (\Throwable $e) {
            Flash::error('Could not update the request: ' . $e->getMessage());
        }
    } elseif (post('action') === 'delete') {
        try {
            Database::run('DELETE FROM market_requests WHERE id = ?', [post_int('id')]);
            Flash::success('Request deleted.');
        } catch (\Throwable $e) {
            Flash::error('Could not delete the request: ' . $e->getMessage());
        }
    }
    redirect(url('market_requests'));
}

try {
    $total = (int) (Database::first('SELECT COUNT(*) c FROM market_requests')['c'] ?? 0);
    $pg    = paginate($total, 20);
    $rows  = Database::all(
        "SELECT r.*, s.name shop_name FROM market_requests r
           LEFT JOIN shops s ON s.id = r.shop_id
          ORDER BY r.id DESC LIMIT {$pg['per']} OFFSET {$pg['offset']}"
    );
} catch (\Throwable $e) {
    echo '<div class="alert alert-danger">Could not load purchase requests: ' . e($e->getMessage()) . '</div>';
    return;
}
$tone = ['new' => 'primary', 'contacted' => 'info', 'approved' => 'success',
         'rejected' => 'danger', 'completed' => 'secondary'];
?>

<div class="card">
  <div class="card-header">Purchase requests <span class="text-secondary small">(<?= $total ?>)</span></div>
  <div class="table-responsive">
    <table class="table align-middle">
      <thead><tr><th>Item</th><th>Shop</th><th>Customer</th><th>Qty</th><th>Total</th><th>Status</th><th>Received</th><th></th></tr></thead>
      <tbody>
      <?php if (!$rows): ?>
        <tr><td colspan="8" class="text-secondary">No requests yet.</td></tr>
      <?php endif; ?>
      <?php foreach ($rows as $r): ?>
        <tr>
          <td><span class="fw-semibold"><?= e($r['item_name'] ?? '—') ?></span>
            <div class="text-secondary small font-monospace"><?= e($r['item_code'] ?? '') ?></div>
            <?php if (!empty($r['note'])): ?><div class="small text-secondary"><?= e($r['note']) ?></div><?php endif; ?>
          </td>
          <td><?= e($r['shop_name'] ?? '—') ?></td>
          <td><?= e($r['contact_name'] ?? '—') ?>
            <div class="small text-secondary"><?= e($r['contact_phone'] ?? '') ?></div>
            <?php if (!empty($r['address'])): ?><div class="small text-secondary"><?= e($r['address']) ?></div><?php endif; ?>
          </td>
          <td><?= (int) $r['quantity'] ?></td>
          <td><?= e(money($r['total_price'] ?? 0)) ?></td>
          <td><span class="badge text-bg-<?= $tone[$r['status'] ?? ''] ?? 'secondary' ?>"><?= e($r['status'] ?? 'new') ?></span></td>
          <td class="small text-secondary"><?= e(nice_date($r['created_at'] ?? null)) ?></td>
          <td class="text-end">
            <form method="post" class="d-inline-flex gap-1">
              <?= Csrf::field() ?>
              <input type="hidden" name="action" value="status">
              <input type="hidden" name="id" value="<?= (int) $r['id'] ?>">
              <select name="status" class="form-select form-select-sm">
                <?php foreach (array_keys($tone) as $s): ?>
                  <option value="<?= $s ?>"<?= ($r['status'] ?? 'new') === $s ? ' selected' : '' ?>><?= $s ?></option>
                <?php endforeach; ?>
              </select>
              <button class="btn btn-sm btn-outline-light">Set</button>
            </form>
          </td>
        </tr>
      <?php endforeach; ?>
      </tbody>
    </table>
  </div>
</div>
