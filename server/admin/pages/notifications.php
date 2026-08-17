<?php
/** Broadcast notifications to shops / all devices. */

use Admin\AdminLog;
use Admin\Csrf;
use Admin\Flash;
use QuickTap\Core\Database;

if (is_post()) {
    $action = post('action');

    if ($action === 'create') {
        $title = post('title');
        $body  = post('body', '');
        if ($title === '' || $body === '') {
            Flash::error('Title and message body are required.');
            redirect(url('notifications'));
        }
        Database::run(
            'INSERT INTO notifications (shop_id, title, body, level, starts_at, ends_at, is_active) VALUES (?,?,?,?,?,?,1)',
            [
                post_int('shop_id') ?: null, $title, $body,
                pick(post('level'), ['info', 'warning', 'critical'], 'info'),
                post_null('starts_at'), post_null('ends_at'),
            ]
        );
        AdminLog::write('notification_sent', 'notification', (string) Database::insertId(), post_int('shop_id') ?: null);
        Flash::success('Notification queued for delivery.');
    } elseif ($action === 'toggle') {
        Database::run('UPDATE notifications SET is_active = 1 - is_active WHERE id = ?', [post_int('id')]);
        Flash::success('Notification visibility toggled.');
    } elseif ($action === 'delete') {
        Database::run('DELETE FROM notifications WHERE id = ?', [post_int('id')]);
        AdminLog::write('notification_deleted', 'notification', (string) post_int('id'));
        Flash::success('Notification deleted.');
    }
    redirect(url('notifications'));
}

$shops = Database::all('SELECT id, name FROM shops WHERE deleted_at IS NULL ORDER BY name');
$total = (int) (Database::first('SELECT COUNT(*) c FROM notifications')['c'] ?? 0);
$pg    = paginate($total, 20);
$rows  = Database::all(
    "SELECT n.*, s.name shop_name FROM notifications n LEFT JOIN shops s ON s.id = n.shop_id
      ORDER BY n.id DESC LIMIT {$pg['per']} OFFSET {$pg['offset']}"
);
$tone = ['info' => 'info', 'warning' => 'warning', 'critical' => 'danger'];
?>

<div class="row g-3">
  <div class="col-lg-5">
    <form method="post" class="card">
      <?= Csrf::field() ?>
      <input type="hidden" name="action" value="create">
      <div class="card-header">Compose notification</div>
      <div class="card-body row g-3">
        <div class="col-12">
          <label class="form-label small">Audience</label>
          <select name="shop_id" class="form-select">
            <option value="">All shops (broadcast)</option>
            <?php foreach ($shops as $s): ?><option value="<?= (int) $s['id'] ?>"><?= e($s['name']) ?></option><?php endforeach; ?>
          </select>
        </div>
        <div class="col-12"><label class="form-label small">Title *</label><input name="title" class="form-control" required></div>
        <div class="col-12"><label class="form-label small">Message *</label><textarea name="body" rows="4" class="form-control" required></textarea></div>
        <div class="col-md-4">
          <label class="form-label small">Level</label>
          <select name="level" class="form-select"><option value="info">Info</option><option value="warning">Warning</option><option value="critical">Critical</option></select>
        </div>
        <div class="col-md-4"><label class="form-label small">Starts</label><input type="datetime-local" name="starts_at" class="form-control"></div>
        <div class="col-md-4"><label class="form-label small">Ends</label><input type="datetime-local" name="ends_at" class="form-control"></div>
      </div>
      <div class="card-footer bg-transparent border-0 text-end pb-3 pe-3"><button class="btn btn-primary">Send</button></div>
    </form>
  </div>

  <div class="col-lg-7">
    <div class="card">
      <div class="card-header">Sent notifications</div>
      <div class="table-responsive">
        <table class="table align-middle">
          <thead><tr><th>Message</th><th>Audience</th><th>Window</th><th>State</th><th></th></tr></thead>
          <tbody>
          <?php foreach ($rows as $n): ?>
            <tr>
              <td>
                <span class="badge text-bg-<?= e($tone[$n['level']] ?? 'secondary') ?> me-1 text-capitalize"><?= e($n['level']) ?></span>
                <span class="fw-semibold"><?= e($n['title']) ?></span>
                <div class="text-secondary small"><?= e(mb_strimwidth((string) $n['body'], 0, 90, '…')) ?></div>
              </td>
              <td class="small"><?= e($n['shop_name'] ?: 'All shops') ?></td>
              <td class="small"><?= e(nice_date($n['starts_at'], 'd M')) ?> → <?= e(nice_date($n['ends_at'], 'd M')) ?></td>
              <td><?= (int) $n['is_active'] ? '<span class="badge text-bg-success">Live</span>' : '<span class="badge text-bg-secondary">Hidden</span>' ?></td>
              <td class="text-end">
                <form method="post" class="d-inline">
                  <?= Csrf::field() ?><input type="hidden" name="action" value="toggle"><input type="hidden" name="id" value="<?= (int) $n['id'] ?>">
                  <button class="btn btn-sm btn-outline-secondary"><?= (int) $n['is_active'] ? 'Hide' : 'Show' ?></button>
                </form>
                <form method="post" class="d-inline" data-confirm="Delete this notification?">
                  <?= Csrf::field() ?><input type="hidden" name="action" value="delete"><input type="hidden" name="id" value="<?= (int) $n['id'] ?>">
                  <button class="btn btn-sm btn-outline-danger">Delete</button>
                </form>
              </td>
            </tr>
          <?php endforeach; ?>
          <?php if (!$rows): ?><tr><td colspan="5" class="empty">No notifications sent yet.</td></tr><?php endif; ?>
          </tbody>
        </table>
      </div>
      <div class="d-flex justify-content-end p-3"><?= pager($pg, 'notifications') ?></div>
    </div>
  </div>
</div>
