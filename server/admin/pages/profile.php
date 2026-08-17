<?php
/** Signed-in admin's own profile and password. */

use Admin\AdminAuth;
use Admin\AdminLog;
use Admin\Csrf;
use Admin\Flash;
use Admin\Session;
use QuickTap\Core\Database;

$me = Database::first('SELECT * FROM super_admins WHERE id = ?', [AdminAuth::id()]);

if (is_post()) {
    if (post('action') === 'profile') {
        Database::run('UPDATE super_admins SET full_name = ?, email = ? WHERE id = ?',
            [post_null('full_name'), post_null('email'), AdminAuth::id()]);
        $session = AdminAuth::user();
        $session['full_name'] = post('full_name') ?: $session['username'];
        Session::set('admin', $session);
        Flash::success('Profile updated.');
    } elseif (post('action') === 'password') {
        $current = (string) ($_POST['current_password'] ?? '');
        $new     = (string) ($_POST['new_password'] ?? '');
        if (!$me || !password_verify($current, (string) $me['password_hash'])) {
            Flash::error('Current password is incorrect.');
        } elseif (strlen($new) < 10) {
            Flash::error('New password must be at least 10 characters.');
        } else {
            Database::run('UPDATE super_admins SET password_hash = ? WHERE id = ?',
                [password_hash($new, PASSWORD_BCRYPT), AdminAuth::id()]);
            AdminLog::write('super_admin_password_changed', 'super_admin', (string) AdminAuth::id());
            Flash::success('Password changed.');
        }
    }
    redirect(url('profile'));
}

$recent = Database::all(
    'SELECT action, entity, entity_id, ip, created_at FROM activity_logs
      WHERE actor_type = "admin" AND actor_id = ? ORDER BY id DESC LIMIT 15',
    [AdminAuth::id()]
);
?>

<div class="row g-3">
  <div class="col-lg-5">
    <form method="post" class="card mb-3">
      <?= Csrf::field() ?>
      <input type="hidden" name="action" value="profile">
      <div class="card-header">Profile</div>
      <div class="card-body row g-3">
        <div class="col-12"><label class="form-label small">Username</label><input class="form-control" value="<?= e($me['username'] ?? '') ?>" disabled></div>
        <div class="col-12"><label class="form-label small">Full name</label><input name="full_name" class="form-control" value="<?= e($me['full_name'] ?? '') ?>"></div>
        <div class="col-12"><label class="form-label small">Email</label><input name="email" type="email" class="form-control" value="<?= e($me['email'] ?? '') ?>"></div>
      </div>
      <div class="card-footer bg-transparent border-0 text-end pb-3 pe-3"><button class="btn btn-primary">Save</button></div>
    </form>

    <form method="post" class="card">
      <?= Csrf::field() ?>
      <input type="hidden" name="action" value="password">
      <div class="card-header">Change password</div>
      <div class="card-body row g-3">
        <div class="col-12"><label class="form-label small">Current password</label><input name="current_password" type="password" class="form-control" required></div>
        <div class="col-12"><label class="form-label small">New password (min 10)</label><input name="new_password" type="password" class="form-control" required minlength="10"></div>
      </div>
      <div class="card-footer bg-transparent border-0 text-end pb-3 pe-3"><button class="btn btn-primary">Update password</button></div>
    </form>
  </div>

  <div class="col-lg-7">
    <div class="card h-100">
      <div class="card-header">My recent actions</div>
      <div class="table-responsive">
        <table class="table align-middle">
          <thead><tr><th>Action</th><th>Entity</th><th>IP</th><th>When</th></tr></thead>
          <tbody>
          <?php foreach ($recent as $r): ?>
            <tr>
              <td class="fw-semibold small"><?= e($r['action']) ?></td>
              <td class="small"><?= e($r['entity'] ?: '—') ?><?= $r['entity_id'] ? ' #' . e($r['entity_id']) : '' ?></td>
              <td class="small font-monospace"><?= e($r['ip'] ?: '—') ?></td>
              <td class="small"><?= e(nice_date($r['created_at'])) ?></td>
            </tr>
          <?php endforeach; ?>
          <?php if (!$recent): ?><tr><td colspan="4" class="empty">No actions recorded yet.</td></tr><?php endif; ?>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</div>
