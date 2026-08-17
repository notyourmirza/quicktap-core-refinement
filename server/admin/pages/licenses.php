<?php
/**
 * Licence control centre — requests, activation, extension, revocation,
 * blocking and custom-day durations. Every expiry below is computed with the
 * SERVER clock; the device clock is never involved.
 */

use Admin\AdminAudit;
use Admin\AdminAuth;
use Admin\AdminLog;
use Admin\Csrf;
use Admin\Flash;
use QuickTap\Core\Database;
use QuickTap\Core\License;

if (!table_exists('licenses')) {
    echo '<div class="alert alert-warning">Run <code>server/sql/migration_license_v3.sql</code> to enable the licence system.</div>';
    return;
}

/** Server-side expiry maths shared by activate / extend / renew. */
function license_expiry_from(?string $base, int $days): ?string
{
    if ($days <= 0) {
        return null; // lifetime
    }
    $from = $base !== null && strtotime($base) > time() ? strtotime($base) : time();
    return date('Y-m-d H:i:s', $from + $days * 86400);
}

function license_days_from_post(): int
{
    $custom = post_int('custom_days');
    if ($custom > 0) {
        return min(3650, $custom);
    }
    return max(0, post_int('duration_days'));
}

if (is_post()) {
    $action    = post('action');
    $licenseId = post_int('license_id');
    $shopId    = post_int('shop_id');
    $license   = $licenseId > 0
        ? Database::first('SELECT * FROM licenses WHERE id = ?', [$licenseId])
        : null;

    if ($action === 'activate' || $action === 'extend' || $action === 'renew') {
        $days = license_days_from_post();

        if (!$license && $shopId > 0) {
            $user = Database::first('SELECT id, device_id FROM users WHERE shop_id = ? AND deleted_at IS NULL ORDER BY id LIMIT 1', [$shopId]);
            Database::run(
                'INSERT INTO licenses (shop_id, user_id, device_id, license_key, status, duration_days)
                 VALUES (?,?,?,?,"PENDING",0)',
                [$shopId, $user['id'] ?? null, $user['device_id'] ?? null, License::generateKey()]
            );
            $license = Database::first('SELECT * FROM licenses WHERE id = ?', [(int) Database::insertId()]);
        }
        if (!$license) {
            Flash::error('Licence not found.');
            redirect(url('licenses'));
        }

        $old     = $license['status'] . ' / ' . ($license['expires_at'] ?? 'lifetime');
        $base    = $action === 'activate' ? null : ($license['expires_at'] ?? null);
        $expires = license_expiry_from($base, $days);
        $planId  = post_int('plan_id') ?: ($license['plan_id'] ?? null);

        Database::run(
            'UPDATE licenses
                SET status = "ACTIVE", duration_days = ?, plan_id = ?,
                    activated_at = COALESCE(activated_at, NOW()), expires_at = ?,
                    issued_by = ?, note = ?
              WHERE id = ?',
            [$days, $planId ?: null, $expires, AdminAuth::id(), post_null('note'), (int) $license['id']]
        );
        Database::run(
            'UPDATE shops SET status = "active", license_status = "ACTIVE",
                    subscription_starts_at = COALESCE(subscription_starts_at, CURDATE()),
                    subscription_ends_at = ?, plan_id = COALESCE(?, plan_id)
              WHERE id = ?',
            [$expires ? date('Y-m-d', strtotime($expires)) : null, $planId ?: null, (int) $license['shop_id']]
        );
        Database::run('UPDATE license_requests SET status = "APPROVED", handled_by = ?, handled_at = NOW()
                        WHERE shop_id = ? AND status = "PENDING"',
            [AdminAuth::id(), (int) $license['shop_id']]);

        AdminAudit::write('license_' . $action, 'license', (string) $license['id'], $old,
            'ACTIVE / ' . ($expires ?? 'lifetime') . ' (' . $days . ' days)');
        AdminLog::write('license_' . $action, 'license', (string) $license['id'], (int) $license['shop_id']);
        Flash::success('Licence ' . $action . 'ed for ' . ($days > 0 ? $days . ' day(s)' : 'lifetime')
            . '. Key: ' . $license['license_key']);
    } elseif (in_array($action, ['revoke', 'suspend', 'resume', 'reactivate'], true) && $license) {
        // resume / reactivate never invent a new expiry: an already-expired
        // licence comes back as EXPIRED, exactly as the server recorded it.
        $new = in_array($action, ['resume', 'reactivate'], true)
            ? (empty($license['expires_at']) || strtotime((string) $license['expires_at']) > time() ? 'ACTIVE' : 'EXPIRED')
            : ($action === 'revoke' ? 'REVOKED' : 'SUSPENDED');
        Database::run('UPDATE licenses SET status = ? WHERE id = ?', [$new, (int) $license['id']]);
        Database::run('UPDATE shops SET license_status = ?, status = ? WHERE id = ?',
            [$new, $new === 'ACTIVE' ? 'active' : 'suspended', (int) $license['shop_id']]);
        AdminLog::write('license_' . $action, 'license', (string) $license['id'], (int) $license['shop_id']);
        AdminAudit::write('license_' . $action, 'license', (string) $license['id'], (string) $license['status'], $new);
        Flash::success('Licence ' . $new . '.');
    } elseif ($action === 'block_user' || $action === 'unblock_user') {
        $userId  = post_int('user_id');
        $blocked = $action === 'block_user' ? 1 : 0;
        if ($userId > 0) {
            Database::run('UPDATE users SET is_blocked = ? WHERE id = ?', [$blocked, $userId]);
            AdminAudit::write($action, 'user', (string) $userId, $blocked ? '0' : '1', (string) $blocked);
            AdminLog::write($action, 'user', (string) $userId);
            Flash::success($blocked ? 'User blocked.' : 'User unblocked.');
        }
    } elseif ($action === 'reject_request') {
        $reqId = post_int('request_id');
        Database::run('UPDATE license_requests SET status = "REJECTED", handled_by = ?, handled_at = NOW() WHERE id = ?',
            [AdminAuth::id(), $reqId]);
        AdminAudit::write('license_request_rejected', 'license_request', (string) $reqId, 'PENDING', 'REJECTED');
        Flash::success('Request rejected.');
    } elseif ($action === 'issue_key' && $shopId > 0) {
        $user = Database::first('SELECT id FROM users WHERE shop_id = ? AND deleted_at IS NULL ORDER BY id LIMIT 1', [$shopId]);
        $key  = License::generateKey();
        Database::run('INSERT INTO licenses (shop_id, user_id, license_key, status, duration_days)
                       VALUES (?,?,?,"PENDING",0)', [$shopId, $user['id'] ?? null, $key]);
        AdminAudit::write('license_key_issued', 'license', (string) Database::insertId(), null, $key);
        Flash::success('New licence key issued: ' . $key);
    }
    redirect(url('licenses', ['tab' => query('tab', 'requests')]));
}

$tab   = pick(query('tab', 'requests'),
    ['requests', 'active', 'expired', 'revoked', 'suspended', 'blocked', 'all'], 'requests');
$plans = Database::all('SELECT id, code, name FROM plans WHERE is_active = 1 ORDER BY sort_order, price');

$requests = Database::all(
    'SELECT lr.*, s.name AS shop_name, u.username, u.id AS uid, u.is_blocked, u.credits,
            u.last_login_at, p.name AS plan_name,
            (SELECT l.status FROM licenses l WHERE l.shop_id = lr.shop_id ORDER BY l.id DESC LIMIT 1) AS license_status,
            (SELECT l.id FROM licenses l WHERE l.shop_id = lr.shop_id ORDER BY l.id DESC LIMIT 1) AS license_id,
            (SELECT l.expires_at FROM licenses l WHERE l.shop_id = lr.shop_id ORDER BY l.id DESC LIMIT 1) AS expires_at
       FROM license_requests lr
       JOIN shops s ON s.id = lr.shop_id
  LEFT JOIN users u ON u.id = lr.user_id
  LEFT JOIN plans p ON p.id = lr.requested_plan_id
      WHERE lr.status = "PENDING"
      ORDER BY lr.created_at DESC LIMIT 200'
);

$statusFilter = ['active' => 'ACTIVE', 'expired' => 'EXPIRED', 'revoked' => 'REVOKED',
                 'suspended' => 'SUSPENDED', 'blocked' => 'BLOCKED'];
$where  = isset($statusFilter[$tab]) ? 'WHERE l.status = ?' : '';
$params = isset($statusFilter[$tab]) ? [$statusFilter[$tab]] : [];
$search = trim(query('q'));
if ($search !== '') {
    $where .= ($where === '' ? 'WHERE ' : ' AND ')
        . '(u.username LIKE ? OR s.name LIKE ? OR l.license_key LIKE ? OR l.device_id LIKE ?)';
    $like   = '%' . $search . '%';
    $params = array_merge($params, [$like, $like, $like, $like]);
}
$licenses = $tab === 'requests' ? [] : Database::all(
    "SELECT l.*, s.name AS shop_name, u.username, u.id AS uid, u.is_blocked, u.credits,
            u.last_login_at, p.name AS plan_name
       FROM licenses l
       JOIN shops s ON s.id = l.shop_id
  LEFT JOIN users u ON u.id = l.user_id
  LEFT JOIN plans p ON p.id = l.plan_id
       {$where}
      ORDER BY l.updated_at DESC LIMIT 300",
    $params
);

$counts = Database::first(
    'SELECT
        (SELECT COUNT(*) FROM license_requests WHERE status = "PENDING") AS pending,
        (SELECT COUNT(*) FROM licenses WHERE status = "ACTIVE")  AS active,
        (SELECT COUNT(*) FROM licenses WHERE status = "EXPIRED") AS expired,
        (SELECT COUNT(*) FROM licenses WHERE status = "REVOKED") AS revoked,
        (SELECT COUNT(*) FROM licenses WHERE status = "SUSPENDED") AS suspended,
        (SELECT COUNT(*) FROM licenses WHERE status = "BLOCKED") AS blocked'
) ?: [];

/** Remaining days, always from the SERVER clock. */
function license_remaining(?string $expiresAt): string
{
    if ($expiresAt === null || $expiresAt === '') {
        return '<span class="badge text-bg-info">Lifetime</span>';
    }
    $days = (int) ceil((strtotime($expiresAt) - time()) / 86400);
    if ($days < 0) {
        return '<span class="badge text-bg-secondary">Expired</span>';
    }
    return '<span class="badge text-bg-' . ($days <= 7 ? 'warning' : 'success') . '">' . $days . ' days</span>';
}

/** Reusable activation form (predefined plans + custom days). */
function license_action_form(array $row, array $plans): string
{
    ob_start(); ?>
  <form method="post" class="row g-1 align-items-center">
    <?= Csrf::field() ?>
    <input type="hidden" name="license_id" value="<?= e($row['license_id'] ?? $row['id'] ?? '') ?>">
    <input type="hidden" name="shop_id" value="<?= e($row['shop_id']) ?>">
    <div class="col-auto">
      <select name="duration_days" class="form-select form-select-sm">
        <option value="1">1 day</option>
        <option value="7">7 days</option>
        <option value="30" selected>30 days</option>
        <option value="90">90 days</option>
        <option value="180">180 days</option>
        <option value="365">365 days (1 year)</option>
        <option value="0">Lifetime</option>
      </select>
    </div>
    <div class="col-auto">
      <input type="number" min="1" max="3650" name="custom_days" class="form-control form-control-sm"
             style="width:120px" placeholder="Custom days">
    </div>
    <div class="col-auto">
      <select name="plan_id" class="form-select form-select-sm">
        <option value="0">Keep plan</option>
        <?php foreach ($plans as $p): ?>
          <option value="<?= e($p['id']) ?>"><?= e($p['name']) ?></option>
        <?php endforeach; ?>
      </select>
    </div>
    <div class="col-auto btn-group btn-group-sm">
      <button class="btn btn-success" name="action" value="activate">Activate</button>
      <button class="btn btn-outline-primary" name="action" value="extend">Extend</button>
      <button class="btn btn-outline-warning" name="action" value="suspend"
              data-confirm="Suspend this licence?">Suspend</button>
      <button class="btn btn-outline-info" name="action" value="resume">Resume</button>
      <button class="btn btn-outline-danger" name="action" value="revoke"
              data-confirm="Revoke this licence? The device locks on its next check.">Revoke</button>
    </div>
  </form>
    <?php return (string) ob_get_clean();
}
?>

<form class="row g-2 mb-3" method="get">
  <input type="hidden" name="p" value="licenses">
  <input type="hidden" name="tab" value="<?= e($tab) ?>">
  <div class="col-sm-5 col-md-4">
    <input class="form-control" name="q" value="<?= e(query('q')) ?>"
           placeholder="Search account, shop, licence key or device">
  </div>
  <div class="col-auto"><button class="btn btn-primary">Search</button></div>
</form>

<ul class="nav nav-pills mb-3 gap-1">
  <?php foreach ([
      'requests' => 'Requests (' . (int) ($counts['pending'] ?? 0) . ')',
      'active'   => 'Active (' . (int) ($counts['active'] ?? 0) . ')',
      'expired'  => 'Expired (' . (int) ($counts['expired'] ?? 0) . ')',
      'revoked'  => 'Revoked (' . (int) ($counts['revoked'] ?? 0) . ')',
      'suspended'=> 'Suspended (' . (int) ($counts['suspended'] ?? 0) . ')',
      'blocked'  => 'Blocked (' . (int) ($counts['blocked'] ?? 0) . ')',
      'all'      => 'All licences',
  ] as $slug => $label): ?>
    <li class="nav-item">
      <a class="nav-link<?= $tab === $slug ? ' active' : '' ?>" href="<?= e(url('licenses', ['tab' => $slug])) ?>"><?= e($label) ?></a>
    </li>
  <?php endforeach; ?>
</ul>

<?php if ($tab === 'requests'): ?>
  <div class="card">
    <div class="card-header">Pending licence requests</div>
    <div class="table-responsive">
      <table class="table table-sm align-middle mb-0">
        <thead><tr>
          <th>Account</th><th>Shop</th><th>Device</th><th>Registered</th>
          <th>Licence</th><th>Requested plan</th><th style="min-width:520px">Action</th>
        </tr></thead>
        <tbody>
        <?php foreach ($requests as $r): ?>
          <tr>
            <td>
              <strong><?= e($r['username'] ?? '—') ?></strong>
              <div class="small text-secondary">Account #<?= e($r['uid'] ?? '—') ?> · <?= (int) ($r['credits'] ?? 0) ?> credits</div>
            </td>
            <td><?= e($r['shop_name']) ?></td>
            <td>
              <?= e($r['device_name'] ?? '—') ?>
              <div class="small text-secondary font-monospace"><?= e(substr((string) $r['device_id'], 0, 12)) ?>…</div>
              <div class="small text-secondary">app <?= e($r['app_version'] ?? '—') ?></div>
            </td>
            <td><?= nice_date($r['created_at']) ?><div class="small text-secondary">last seen <?= nice_date($r['last_login_at']) ?></div></td>
            <td><?= status_badge(strtolower((string) ($r['license_status'] ?? 'pending'))) ?>
              <div class="small text-secondary"><?= nice_date($r['expires_at']) ?></div></td>
            <td><?= e($r['plan_name'] ?? '—') ?></td>
            <td>
              <?= license_action_form($r, $plans) ?>
              <form method="post" class="mt-1 d-flex gap-1">
                <?= Csrf::field() ?>
                <input type="hidden" name="request_id" value="<?= e($r['id']) ?>">
                <input type="hidden" name="user_id" value="<?= e($r['uid'] ?? 0) ?>">
                <button class="btn btn-sm btn-outline-secondary" name="action" value="reject_request">Reject request</button>
                <?php if ((int) ($r['is_blocked'] ?? 0) === 1): ?>
                  <button class="btn btn-sm btn-outline-success" name="action" value="unblock_user">Unblock user</button>
                <?php else: ?>
                  <button class="btn btn-sm btn-outline-danger" name="action" value="block_user">Block user</button>
                <?php endif; ?>
              </form>
            </td>
          </tr>
        <?php endforeach; ?>
        <?php if (!$requests): ?>
          <tr><td colspan="7" class="text-secondary p-3">No pending requests.</td></tr>
        <?php endif; ?>
        </tbody>
      </table>
    </div>
  </div>
<?php else: ?>
  <div class="card">
    <div class="card-header text-capitalize"><?= e($tab) ?> licences</div>
    <div class="table-responsive">
      <table class="table table-sm align-middle mb-0">
        <thead><tr>
          <th>Licence key</th><th>Account</th><th>Device</th><th>Status</th>
          <th>Duration</th><th>Start</th><th>Expiry</th><th>Remaining</th><th>Created</th>
          <th style="min-width:560px">Action</th>
        </tr></thead>
        <tbody>
        <?php foreach ($licenses as $l): ?>
          <tr>
            <td class="font-monospace"><?= e($l['license_key']) ?></td>
            <td><?= e($l['username'] ?? '—') ?>
              <div class="small text-secondary"><?= (int) ($l['credits'] ?? 0) ?> credits</div></td>
            <td><?= e($l['shop_name']) ?>
              <div class="small text-secondary font-monospace"><?= e(substr((string) ($l['device_id'] ?? ''), 0, 12)) ?></div></td>
            <td><?= status_badge(strtolower((string) $l['status'])) ?></td>
            <td><?= (int) $l['duration_days'] === 0 ? 'Lifetime' : (int) $l['duration_days'] . ' days' ?></td>
            <td><?= nice_date($l['activated_at'], 'd M Y') ?></td>
            <td><?= nice_date($l['expires_at'], 'd M Y') ?></td>
            <td><?= license_remaining($l['expires_at'] ?? null) ?></td>
            <td><?= nice_date($l['created_at'], 'd M Y') ?>
              <div class="small text-secondary">checked <?= nice_date($l['last_verified_at'], 'd M H:i') ?></div></td>
            <td>
              <?= license_action_form($l + ['license_id' => $l['id']], $plans) ?>
              <form method="post" class="mt-1">
                <?= Csrf::field() ?>
                <input type="hidden" name="user_id" value="<?= e($l['uid'] ?? 0) ?>">
                <?php if ((int) ($l['is_blocked'] ?? 0) === 1): ?>
                  <button class="btn btn-sm btn-outline-success" name="action" value="unblock_user">Unblock user</button>
                <?php else: ?>
                  <button class="btn btn-sm btn-outline-danger" name="action" value="block_user">Block user</button>
                <?php endif; ?>
              </form>
            </td>
          </tr>
        <?php endforeach; ?>
        <?php if (!$licenses): ?>
          <tr><td colspan="10" class="text-secondary p-3">No licences match this view.</td></tr>
        <?php endif; ?>
        </tbody>
      </table>
    </div>
  </div>
<?php endif; ?>
