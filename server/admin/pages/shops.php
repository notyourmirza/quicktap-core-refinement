<?php
/** Shops list — search, filter, create, suspend/activate, delete. */

use Admin\AdminLog;
use Admin\Csrf;
use Admin\Flash;
use QuickTap\Core\Database;

$plans = Database::all('SELECT id, name, code FROM plans WHERE is_active = 1 ORDER BY price ASC');

if (is_post()) {
    $action = post('action');

    if ($action === 'create') {
        $name  = post('name', '');
        $owner = post_null('owner_name');
        $uname = post('username');
        $pass  = (string) ($_POST['password'] ?? '');

        if ($name === '' || $uname === '' || strlen($pass) < 8) {
            Flash::error('Shop name, owner username and a password of at least 8 characters are required.');
        } else {
            // ---- plan assignment (admin is the only authority) ----------------
            $planId  = post_int('plan_id') ?: null;
            $months  = post_int('plan_duration');
            $cycle   = null;
            if ($planId) {
                $cycle = Database::first('SELECT billing_cycle FROM plans WHERE id = ?', [$planId])['billing_cycle'] ?? null;
            }
            [$startsAt, $endsAt] = $planId
                ? plan_window(post_null('subscription_starts_at'), post_null('subscription_ends_at'), $months, $cycle)
                : [post_null('subscription_starts_at'), post_null('subscription_ends_at')];
            $wanted = post('plan_status') === 'inactive' ? 'suspended' : 'active';
            $status = plan_effective_status($planId, $wanted, $endsAt);

            try {
                Database::transaction(function () use ($name, $owner, $uname, $pass, $planId, $status, $startsAt, $endsAt) {
                    $uuid = uuid4();
                    Database::run(
                        'INSERT INTO shops (uuid, name, owner_name, phone, email, address, currency, plan_id, status,
                                            subscription_starts_at, subscription_ends_at)
                         VALUES (?,?,?,?,?,?,?,?,?,?,?)',
                        [
                            $uuid, $name, $owner, post_null('phone'), post_null('email'), post_null('address'),
                            post('currency', 'Rs') ?: 'Rs',
                            $planId, $status, $startsAt, $endsAt,
                        ]
                    );
                    $shopId = Database::insertId();
                    Database::run(
                        'INSERT INTO users (shop_id, username, password_hash, full_name, role) VALUES (?,?,?,?,"owner")',
                        [$shopId, $uname, password_hash($pass, PASSWORD_BCRYPT), $owner]
                    );
                    Database::run(
                        'INSERT INTO themes (shop_id, primary_color, secondary_color, app_name) VALUES (?,?,?,?)',
                        [$shopId, '#0E9F6E', '#34D399', 'QuickTap POS']
                    );
                    AdminLog::write('shop_created', 'shop', (string) $shopId, $shopId,
                        ['name' => $name, 'plan_id' => $planId, 'status' => $status, 'ends_at' => $endsAt]);
                });
                Flash::success($planId
                    ? 'Shop created and the plan was assigned. The device picks it up on its next sync.'
                    : 'Shop created with its owner account.');

            } catch (\PDOException $e) {
                Flash::error('Could not create shop — the username may already be taken.');
            }
        }
        redirect(url('shops'));
    }

    $shopId = post_int('shop_id');
    if ($shopId > 0 && $action === 'status') {
        $status = pick(post('status'), ['active', 'suspended', 'expired', 'pending'], 'active');
        Database::run('UPDATE shops SET status = ? WHERE id = ?', [$status, $shopId]);
        AdminLog::write('shop_status_changed', 'shop', (string) $shopId, $shopId, ['status' => $status]);
        Flash::success('Shop status updated to ' . $status . '.');
        redirect(url('shops'));
    }

    if ($shopId > 0 && $action === 'delete') {
        Database::run('UPDATE shops SET deleted_at = NOW(), status = "suspended" WHERE id = ?', [$shopId]);
        AdminLog::write('shop_deleted', 'shop', (string) $shopId, $shopId);
        Flash::success('Shop archived. Data is retained and can be restored from the database.');
        redirect(url('shops'));
    }
}

$search = query('q');
$status = pick(query('status'), ['active', 'suspended', 'expired', 'pending'], '');
$planId = query_int('plan_id');

$where  = ['s.deleted_at IS NULL'];
$params = [];
if ($search !== '') {
    $where[]  = '(s.name LIKE ? OR s.phone LIKE ? OR s.email LIKE ? OR s.owner_name LIKE ?)';
    $like     = '%' . $search . '%';
    $params[] = $like; $params[] = $like; $params[] = $like; $params[] = $like;
}
if ($status !== '') { $where[] = 's.status = ?';  $params[] = $status; }
if ($planId > 0)    { $where[] = 's.plan_id = ?'; $params[] = $planId; }
$whereSql = 'WHERE ' . implode(' AND ', $where);

$total = (int) (Database::first("SELECT COUNT(*) c FROM shops s $whereSql", $params)['c'] ?? 0);
$pg    = paginate($total, 15);

$rows = Database::all(
    "SELECT s.*, p.name plan_name,
            (SELECT COUNT(*) FROM users u WHERE u.shop_id = s.id AND u.deleted_at IS NULL) users_count,
            (SELECT COUNT(*) FROM devices d WHERE d.shop_id = s.id AND d.status = 'active') devices_count
       FROM shops s LEFT JOIN plans p ON p.id = s.plan_id
       $whereSql ORDER BY s.created_at DESC LIMIT {$pg['per']} OFFSET {$pg['offset']}",
    $params
);
$filters = array_filter(['q' => $search, 'status' => $status, 'plan_id' => $planId ?: '']);
?>

<div class="d-flex flex-wrap gap-2 align-items-center mb-3">
  <form class="d-flex gap-2 flex-wrap" method="get">
    <input type="hidden" name="p" value="shops">
    <input name="q" class="form-control form-control-sm" style="width:230px" placeholder="Search shop, owner, phone…" value="<?= e($search) ?>">
    <select name="status" class="form-select form-select-sm" style="width:150px">
      <option value="">All statuses</option>
      <?php foreach (['active', 'pending', 'suspended', 'expired'] as $s): ?>
        <option value="<?= $s ?>" <?= $status === $s ? 'selected' : '' ?>><?= ucfirst($s) ?></option>
      <?php endforeach; ?>
    </select>
    <select name="plan_id" class="form-select form-select-sm" style="width:160px">
      <option value="">All plans</option>
      <?php foreach ($plans as $pl): ?>
        <option value="<?= (int) $pl['id'] ?>" <?= $planId === (int) $pl['id'] ? 'selected' : '' ?>><?= e($pl['name']) ?></option>
      <?php endforeach; ?>
    </select>
    <button class="btn btn-sm btn-outline-secondary">Filter</button>
  </form>
  <button class="btn btn-sm btn-primary ms-auto" data-bs-toggle="modal" data-bs-target="#newShop">
    <i class="bi bi-plus-lg"></i> New shop
  </button>
</div>

<div class="card">
  <div class="table-responsive">
    <table class="table align-middle">
      <thead>
        <tr><th>Shop</th><th>Plan</th><th>Users</th><th>Devices</th><th>Subscription</th><th>Status</th><th></th></tr>
      </thead>
      <tbody>
      <?php foreach ($rows as $r): ?>
        <tr>
          <td>
            <a class="link-light fw-semibold" href="<?= e(url('shop', ['id' => (int) $r['id']])) ?>"><?= e($r['name']) ?></a>
            <div class="text-secondary small"><?= e($r['owner_name'] ?: '—') ?> · <?= e($r['phone'] ?: 'no phone') ?></div>
          </td>
          <td><?= e($r['plan_name'] ?: '—') ?></td>
          <td><?= (int) $r['users_count'] ?></td>
          <td><?= (int) $r['devices_count'] ?></td>
          <td class="small"><?= e(nice_date($r['subscription_ends_at'], 'd M Y')) ?></td>
          <td><?= status_badge((string) $r['status']) ?></td>
          <td class="text-end">
            <div class="btn-group btn-group-sm">
              <a class="btn btn-outline-secondary" href="<?= e(url('shop', ['id' => (int) $r['id']])) ?>">Manage</a>
              <button class="btn btn-outline-secondary dropdown-toggle" data-bs-toggle="dropdown"></button>
              <ul class="dropdown-menu dropdown-menu-end">
                <li>
                  <form method="post" class="px-2 py-1">
                    <?= Csrf::field() ?>
                    <input type="hidden" name="action" value="status">
                    <input type="hidden" name="shop_id" value="<?= (int) $r['id'] ?>">
                    <input type="hidden" name="status" value="<?= $r['status'] === 'active' ? 'suspended' : 'active' ?>">
                    <button class="btn btn-sm btn-link p-0 text-decoration-none">
                      <?= $r['status'] === 'active' ? 'Suspend shop' : 'Activate shop' ?>
                    </button>
                  </form>
                </li>
                <li><hr class="dropdown-divider"></li>
                <li>
                  <form method="post" class="px-2 py-1" data-confirm="Archive this shop? Devices will stop syncing.">
                    <?= Csrf::field() ?>
                    <input type="hidden" name="action" value="delete">
                    <input type="hidden" name="shop_id" value="<?= (int) $r['id'] ?>">
                    <button class="btn btn-sm btn-link p-0 text-danger text-decoration-none">Archive shop</button>
                  </form>
                </li>
              </ul>
            </div>
          </td>
        </tr>
      <?php endforeach; ?>
      <?php if (!$rows): ?><tr><td colspan="7" class="empty">No shops match these filters.</td></tr><?php endif; ?>
      </tbody>
    </table>
  </div>
  <div class="d-flex justify-content-between align-items-center p-3">
    <span class="small text-secondary"><?= $total ?> shops</span>
    <?= pager($pg, 'shops', $filters) ?>
  </div>
</div>

<div class="modal fade" id="newShop" tabindex="-1">
  <div class="modal-dialog modal-lg modal-dialog-centered">
    <form class="modal-content" method="post">
      <?= Csrf::field() ?>
      <input type="hidden" name="action" value="create">
      <div class="modal-header border-0"><h5 class="modal-title">Create shop</h5>
        <button type="button" class="btn-close" data-bs-dismiss="modal"></button></div>
      <div class="modal-body">
        <div class="row g-3">
          <div class="col-md-6"><label class="form-label small">Shop name *</label><input name="name" class="form-control" required></div>
          <div class="col-md-6"><label class="form-label small">Owner name</label><input name="owner_name" class="form-control"></div>
          <div class="col-md-4"><label class="form-label small">Phone</label><input name="phone" class="form-control"></div>
          <div class="col-md-4"><label class="form-label small">Email</label><input name="email" type="email" class="form-control"></div>
          <div class="col-md-4"><label class="form-label small">Currency</label><input name="currency" class="form-control" value="Rs"></div>
          <div class="col-12"><label class="form-label small">Address</label><input name="address" class="form-control"></div>
          <div class="col-12"><hr class="border-secondary"><div class="small text-secondary mb-1">Plan &amp; subscription — assigned by you, the device never buys it.</div></div>
          <div class="col-md-4">
            <label class="form-label small">Plan</label>
            <select name="plan_id" class="form-select">
              <option value="">No plan</option>
              <?php foreach ($plans as $pl): ?><option value="<?= (int) $pl['id'] ?>"><?= e($pl['name']) ?></option><?php endforeach; ?>
            </select>
          </div>
          <div class="col-md-4">
            <label class="form-label small">Plan status</label>
            <select name="plan_status" class="form-select">
              <option value="active">Active</option>
              <option value="inactive">Inactive</option>
            </select>
          </div>
          <div class="col-md-4">
            <label class="form-label small">Duration</label>
            <select name="plan_duration" class="form-select">
              <option value="0">Plan default (billing cycle)</option>
              <?php foreach (plan_durations() as $m => $label): if ($m === 0) continue; ?>
                <option value="<?= (int) $m ?>" <?= $m === 1 ? 'selected' : '' ?>><?= e($label) ?></option>
              <?php endforeach; ?>
            </select>
          </div>
          <div class="col-md-3"><label class="form-label small">Activated on</label><input type="date" name="subscription_starts_at" class="form-control"></div>
          <div class="col-md-3"><label class="form-label small">Expires on (optional)</label><input type="date" name="subscription_ends_at" class="form-control"></div>
          <div class="col-md-6 d-flex align-items-end"><div class="small text-secondary">Leave the expiry empty to calculate it from the duration.</div></div>

          <div class="col-12"><hr class="border-secondary"></div>
          <div class="col-md-6"><label class="form-label small">Owner username *</label><input name="username" class="form-control" required autocomplete="off"></div>
          <div class="col-md-6"><label class="form-label small">Owner password * (min 8)</label><input name="password" type="text" class="form-control" required minlength="8" autocomplete="new-password"></div>
        </div>
      </div>
      <div class="modal-footer border-0">
        <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Cancel</button>
        <button class="btn btn-primary">Create shop</button>
      </div>
    </form>
  </div>
</div>
