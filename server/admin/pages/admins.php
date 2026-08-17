<?php
/** Manage other super admin accounts. */

use Admin\AdminAuth;
use Admin\AdminLog;
use Admin\Csrf;
use Admin\Flash;
use QuickTap\Core\Database;

if (is_post()) {
    $action = post('action');
    $id     = post_int('id');

    if ($action === 'create') {
        $username = post('username');
        $password = (string) ($_POST['password'] ?? '');
        if ($username === '' || strlen($password) < 10) {
            Flash::error('Username and a password of at least 10 characters are required.');
            redirect(url('admins'));
        }
        try {
            Database::run(
                'INSERT INTO super_admins (username, password_hash, full_name, email) VALUES (?,?,?,?)',
                [$username, password_hash($password, PASSWORD_BCRYPT), post_null('full_name'), post_null('email')]
            );
            AdminLog::write('super_admin_created', 'super_admin', (string) Database::insertId());
            Flash::success('Super admin created.');
        } catch (\PDOException) {
            Flash::error('That username is already taken.');
        }
    } elseif ($action === 'toggle' && $id > 0) {
        if ($id === AdminAuth::id()) {
            Flash::error('You cannot disable your own account.');
        } else {
            Database::run('UPDATE super_admins SET is_active = 1 - is_active WHERE id = ?', [$id]);
            AdminLog::write('super_admin_toggled', 'super_admin', (string) $id);
            Flash::success('Account status changed.');
        }
    } elseif ($action === 'reset' && $id > 0) {
        $password = (string) ($_POST['password'] ?? '');
        if (strlen($password) < 10) {
            Flash::error('Password must be at least 10 characters.');
        } else {
            Database::run('UPDATE super_admins SET password_hash = ? WHERE id = ?', [password_hash($password, PASSWORD_BCRYPT), $id]);
            AdminLog::write('super_admin_password_reset', 'super_admin', (string) $id);
            Flash::success('Password reset.');
        }
    } elseif ($action === 'delete' && $id > 0) {
        if ($id === AdminAuth::id()) {
            Flash::error('You cannot delete your own account.');
        } else {
            Database::run('DELETE FROM super_admins WHERE id = ?', [$id]);
            AdminLog::write('super_admin_deleted', 'super_admin', (string) $id);
            Flash::success('Super admin deleted.');
        }
    }
    redirect(url('admins'));
}

$admins = Database::all('SELECT id, username, full_name, email, is_active, last_login_at, created_at FROM super_admins ORDER BY id ASC');
?>

<div class="d-flex mb-3">
  <button class="btn btn-sm btn-primary ms-auto" data-bs-toggle="modal" data-bs-target="#newAdmin"><i class="bi bi-plus-lg"></i> Add super admin</button>
</div>

<div class="card">
  <div class="table-responsive">
    <table class="table align-middle">
      <thead><tr><th>Admin</th><th>Email</th><th>Last login</th><th>Status</th><th></th></tr></thead>
      <tbody>
      <?php foreach ($admins as $a): ?>
        <tr>
          <td><span class="fw-semibold"><?= e($a['username']) ?></span>
            <?php if ((int) $a['id'] === AdminAuth::id()): ?><span class="badge text-bg-info ms-1">You</span><?php endif; ?>
            <div class="text-secondary small"><?= e($a['full_name'] ?: '—') ?></div></td>
          <td class="small"><?= e($a['email'] ?: '—') ?></td>
          <td class="small"><?= e(nice_date($a['last_login_at'])) ?></td>
          <td><?= (int) $a['is_active'] ? '<span class="badge text-bg-success">Active</span>' : '<span class="badge text-bg-secondary">Disabled</span>' ?></td>
          <td class="text-end">
            <button class="btn btn-sm btn-outline-secondary" data-bs-toggle="modal" data-bs-target="#reset<?= (int) $a['id'] ?>">Reset password</button>
            <form method="post" class="d-inline">
              <?= Csrf::field() ?><input type="hidden" name="action" value="toggle"><input type="hidden" name="id" value="<?= (int) $a['id'] ?>">
              <button class="btn btn-sm btn-outline-secondary"><?= (int) $a['is_active'] ? 'Disable' : 'Enable' ?></button>
            </form>
            <form method="post" class="d-inline" data-confirm="Delete this super admin?">
              <?= Csrf::field() ?><input type="hidden" name="action" value="delete"><input type="hidden" name="id" value="<?= (int) $a['id'] ?>">
              <button class="btn btn-sm btn-outline-danger">Delete</button>
            </form>
          </td>
        </tr>
      <?php endforeach; ?>
      </tbody>
    </table>
  </div>
</div>

<?php foreach ($admins as $a): ?>
  <div class="modal fade" id="reset<?= (int) $a['id'] ?>" tabindex="-1">
    <div class="modal-dialog modal-dialog-centered">
      <form class="modal-content" method="post">
        <?= Csrf::field() ?>
        <input type="hidden" name="action" value="reset"><input type="hidden" name="id" value="<?= (int) $a['id'] ?>">
        <div class="modal-header border-0"><h5 class="modal-title">Reset password — <?= e($a['username']) ?></h5>
          <button type="button" class="btn-close" data-bs-dismiss="modal"></button></div>
        <div class="modal-body">
          <label class="form-label small">New password (min 10 characters)</label>
          <input name="password" type="text" class="form-control" required minlength="10" autocomplete="new-password">
        </div>
        <div class="modal-footer border-0">
          <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Cancel</button>
          <button class="btn btn-primary">Reset</button>
        </div>
      </form>
    </div>
  </div>
<?php endforeach; ?>

<div class="modal fade" id="newAdmin" tabindex="-1">
  <div class="modal-dialog modal-dialog-centered">
    <form class="modal-content" method="post">
      <?= Csrf::field() ?>
      <input type="hidden" name="action" value="create">
      <div class="modal-header border-0"><h5 class="modal-title">Add super admin</h5>
        <button type="button" class="btn-close" data-bs-dismiss="modal"></button></div>
      <div class="modal-body row g-3">
        <div class="col-md-6"><label class="form-label small">Username *</label><input name="username" class="form-control" required autocomplete="off"></div>
        <div class="col-md-6"><label class="form-label small">Password * (min 10)</label><input name="password" type="text" class="form-control" required minlength="10" autocomplete="new-password"></div>
        <div class="col-md-6"><label class="form-label small">Full name</label><input name="full_name" class="form-control"></div>
        <div class="col-md-6"><label class="form-label small">Email</label><input name="email" type="email" class="form-control"></div>
      </div>
      <div class="modal-footer border-0">
        <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Cancel</button>
        <button class="btn btn-primary">Create</button>
      </div>
    </form>
  </div>
</div>
