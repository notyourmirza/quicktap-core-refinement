<?php
/**
 * Credit control — grant, deduct or set an account's credit balance.
 * Every movement is written to credit_transactions so the balance is always
 * reconstructable from the ledger, and to admin_audit_logs for accountability.
 */

use Admin\AdminAudit;
use Admin\AdminAuth;
use Admin\Csrf;
use Admin\Flash;
use QuickTap\Core\Database;

if (!table_exists('credit_transactions') || !has_column('users', 'credits')) {
    echo '<div class="alert alert-warning">Run <code>server/sql/migration_license_v3.sql</code> to enable credit management.</div>';
    return;
}

if (is_post()) {
    $userId = post_int('user_id');
    $action = post('action');
    $amount = abs(post_int('amount'));
    $reason = post_null('reason');

    $user = $userId > 0
        ? Database::first('SELECT id, shop_id, username, credits FROM users WHERE id = ? AND deleted_at IS NULL', [$userId])
        : null;

    if (!$user) {
        Flash::error('Account not found.');
        redirect(url('credits'));
    }

    $before = (int) $user['credits'];
    $after  = match ($action) {
        'add'    => $before + $amount,
        'deduct' => max(0, $before - $amount),
        'set'    => $amount,
        default  => $before,
    };

    if ($after !== $before) {
        Database::transaction(static function () use ($user, $before, $after, $action, $reason) {
            Database::run('UPDATE users SET credits = ? WHERE id = ?', [$after, (int) $user['id']]);
            Database::run(
                'INSERT INTO credit_transactions (user_id, shop_id, delta, balance_after, type, reason, admin_id)
                 VALUES (?,?,?,?,?,?,?)',
                [
                    (int) $user['id'], (int) $user['shop_id'], $after - $before, $after,
                    $action === 'deduct' ? 'DEDUCT' : ($action === 'set' ? 'ADJUST' : 'GRANT'),
                    $reason, AdminAuth::id() ?: null,
                ]
            );
        });
        AdminAudit::write('credits_' . $action, 'user', (string) $user['id'], (string) $before, (string) $after);
        Flash::success('Credits for ' . $user['username'] . ': ' . $before . ' → ' . $after);
    } else {
        Flash::error('Nothing changed.');
    }
    redirect(url('credits', ['q' => query('q')]));
}

$q     = trim(query('q'));
$like  = '%' . $q . '%';
$users = Database::all(
    'SELECT u.id, u.username, u.full_name, u.credits, u.is_blocked, u.last_login_at,
            s.name AS shop_name,
            (SELECT l.status FROM licenses l WHERE l.shop_id = u.shop_id ORDER BY l.id DESC LIMIT 1) AS license_status
       FROM users u
       JOIN shops s ON s.id = u.shop_id
      WHERE u.deleted_at IS NULL' . ($q !== '' ? ' AND (u.username LIKE ? OR u.full_name LIKE ? OR s.name LIKE ?)' : '') . '
      ORDER BY u.last_login_at DESC, u.id DESC
      LIMIT 200',
    $q !== '' ? [$like, $like, $like] : []
);

$ledger = Database::all(
    'SELECT ct.*, u.username, a.username AS admin_name
       FROM credit_transactions ct
       JOIN users u ON u.id = ct.user_id
  LEFT JOIN super_admins a ON a.id = ct.admin_id
      ORDER BY ct.id DESC LIMIT 60'
);
?>

<form class="row g-2 mb-3" method="get">
  <input type="hidden" name="page" value="credits">
  <div class="col-auto">
    <input class="form-control" name="q" value="<?= e($q) ?>" placeholder="Search account, name or shop">
  </div>
  <div class="col-auto"><button class="btn btn-primary">Search</button></div>
</form>

<div class="card mb-4">
  <div class="card-header">Accounts &amp; credits</div>
  <div class="table-responsive">
    <table class="table table-sm align-middle mb-0">
      <thead><tr>
        <th>Account</th><th>Shop</th><th>Licence</th><th>Credits</th><th style="min-width:460px">Adjust</th>
      </tr></thead>
      <tbody>
      <?php foreach ($users as $u): ?>
        <tr>
          <td><strong><?= e($u['username']) ?></strong>
            <div class="small text-secondary"><?= e($u['full_name'] ?? '') ?>
              <?= (int) $u['is_blocked'] === 1 ? '<span class="badge bg-danger ms-1">blocked</span>' : '' ?></div>
          </td>
          <td><?= e($u['shop_name']) ?></td>
          <td><?= status_badge(strtolower((string) ($u['license_status'] ?? 'pending'))) ?></td>
          <td class="fs-5"><?= (int) $u['credits'] ?></td>
          <td>
            <form method="post" class="row g-1 align-items-center">
              <?= Csrf::field() ?>
              <input type="hidden" name="user_id" value="<?= e($u['id']) ?>">
              <div class="col-auto">
                <input type="number" min="0" name="amount" class="form-control form-control-sm"
                       style="width:110px" placeholder="Amount" required>
              </div>
              <div class="col">
                <input name="reason" class="form-control form-control-sm" placeholder="Reason (optional)">
              </div>
              <div class="col-auto btn-group btn-group-sm">
                <button class="btn btn-success" name="action" value="add">Add</button>
                <button class="btn btn-outline-warning" name="action" value="deduct">Deduct</button>
                <button class="btn btn-outline-secondary" name="action" value="set">Set</button>
              </div>
            </form>
          </td>
        </tr>
      <?php endforeach; ?>
      <?php if (!$users): ?>
        <tr><td colspan="5" class="text-secondary p-3">No accounts found.</td></tr>
      <?php endif; ?>
      </tbody>
    </table>
  </div>
</div>

<div class="card">
  <div class="card-header">Recent credit movements</div>
  <div class="table-responsive">
    <table class="table table-sm mb-0">
      <thead><tr><th>When</th><th>Account</th><th>Change</th><th>Balance</th><th>Type</th><th>Reason</th><th>By</th></tr></thead>
      <tbody>
      <?php foreach ($ledger as $t): ?>
        <tr>
          <td><?= nice_date($t['created_at']) ?></td>
          <td><?= e($t['username']) ?></td>
          <td class="<?= (int) $t['delta'] >= 0 ? 'text-success' : 'text-danger' ?>">
            <?= (int) $t['delta'] > 0 ? '+' : '' ?><?= (int) $t['delta'] ?>
          </td>
          <td><?= (int) $t['balance_after'] ?></td>
          <td><?= e($t['type']) ?></td>
          <td><?= e($t['reason'] ?? '—') ?></td>
          <td><?= e($t['admin_name'] ?? 'system') ?></td>
        </tr>
      <?php endforeach; ?>
      <?php if (!$ledger): ?>
        <tr><td colspan="7" class="text-secondary p-3">No movements yet.</td></tr>
      <?php endif; ?>
      </tbody>
    </table>
  </div>
</div>
