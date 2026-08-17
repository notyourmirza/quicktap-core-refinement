<?php

/** The ten premium design languages shipped in the Android app (ThemePresets.java). */
$THEMES = [
    'material_you'     => ['Material You',           '#6750A4', '#7D5260'],
    'minimal_luxury'   => ['Minimal Luxury',         '#B08D3F', '#D9BE73'],
    'glassmorphism'    => ['Glassmorphism',          '#5B8DEF', '#8E7CF5'],
    'neo_banking'      => ['Neo Banking',            '#1B4DFF', '#3E7BFA'],
    'dark_pro'         => ['Dark Pro (AMOLED)',      '#00E5A0', '#38BDF8'],
    'modern_retail'    => ['Modern Retail',          '#FF5A1F', '#FFB020'],
    'elegant_business' => ['Elegant Business',       '#12386B', '#2C6EB5'],
    'soft_pastel'      => ['Soft Pastel',            '#8E7CF5', '#7FD1C1'],
    'black_gold'       => ['Premium Black & Gold',   '#D4AF37', '#F0DB8C'],
    'futuristic_ai'    => ['Futuristic AI',          '#7C3AED', '#22D3EE'],
];
/** Shop detail — profile, subscription, staff, devices, theme, features, settings, sales, backups. */

use Admin\AdminLog;
use Admin\Csrf;
use Admin\Flash;
use QuickTap\Core\Database;

$shopId = query_int('id');
$shop   = $shopId > 0 ? Database::first('SELECT * FROM shops WHERE id = ? LIMIT 1', [$shopId]) : null;

if (!$shop) {
    Flash::error('Shop not found.');
    redirect(url('shops'));
}

$FEATURES = [
    'offline_mode'      => 'Offline mode & local SQLite',
    'auto_sync'         => 'Automatic background sync',
    'fingerprint_login' => 'Fingerprint / biometric login',
    'gdrive_backup'     => 'Google Drive backup',
    'barcode_scanner'   => 'Barcode scanning',
    'thermal_printing'  => 'Thermal receipt printing',
    'customer_credit'   => 'Customer credit / khata',
    'expenses'          => 'Expense tracking',
    'multi_user'        => 'Multiple staff accounts',
    'reports'           => 'Advanced reports',
];

$SETTINGS = [
    'session_lock_minutes' => ['Auto session lock (minutes)', 'int', '5'],
    'sync_interval_minutes'=> ['Sync interval (minutes)', 'int', '15'],
    'backup_interval_hours'=> ['Auto backup interval (hours)', 'int', '24'],
    'low_stock_threshold'  => ['Low stock alert threshold', 'int', '5'],
    'receipt_footer'       => ['Receipt footer text', 'string', 'Thank you!'],
    'tax_percent_default'  => ['Default tax percent', 'string', '0'],
    'invoice_prefix'       => ['Invoice number prefix', 'string', 'INV-'],
];

if (is_post()) {
    $action = post('action');

    switch ($action) {
        case 'profile':
            Database::run(
                'UPDATE shops SET name=?, owner_name=?, phone=?, email=?, address=?, currency=? WHERE id=?',
                [
                    post('name') ?: $shop['name'], post_null('owner_name'), post_null('phone'), post_null('email'),
                    post_null('address'), post('currency', 'Rs') ?: 'Rs', $shopId,
                ]
            );
            AdminLog::write('shop_updated', 'shop', (string) $shopId, $shopId);
            Flash::success('Shop profile saved.');
            break;

        // ---- plan / subscription: the admin is the only authority ----------
        case 'plan':
            $mode    = pick(post('mode'), ['save', 'activate', 'deactivate', 'renew', 'extend'], 'save');
            $planId  = $mode === 'save' ? (post_int('plan_id') ?: null) : ((int) $shop['plan_id'] ?: null);
            $months  = post_int('plan_duration');
            $cycle   = $planId
                ? (Database::first('SELECT billing_cycle FROM plans WHERE id = ?', [$planId])['billing_cycle'] ?? null)
                : null;

            $startsAt = $shop['subscription_starts_at'];
            $endsAt   = $shop['subscription_ends_at'];
            $status   = (string) $shop['status'];

            if ($mode === 'save') {
                [$startsAt, $endsAt] = $planId
                    ? plan_window(post_null('subscription_starts_at'), post_null('subscription_ends_at'), $months, $cycle)
                    : [post_null('subscription_starts_at'), post_null('subscription_ends_at')];
                $status = plan_effective_status($planId, post('plan_status') === 'inactive' ? 'suspended' : 'active', $endsAt);
            } elseif ($mode === 'activate') {
                if (!$planId) { Flash::error('Assign a plan first.'); break; }
                if (plan_is_expired($endsAt)) {
                    [$startsAt, $endsAt] = plan_window(date('Y-m-d'), null, $months, $cycle);
                }
                $status = 'active';
            } elseif ($mode === 'deactivate') {
                $status = 'suspended';
            } elseif ($mode === 'renew') {
                if (!$planId) { Flash::error('Assign a plan first.'); break; }
                [$startsAt, $endsAt] = plan_window(date('Y-m-d'), null, $months, $cycle);
                $status = 'active';
            } elseif ($mode === 'extend') {
                if (!$planId) { Flash::error('Assign a plan first.'); break; }
                $base = plan_is_expired($endsAt) || !$endsAt ? date('Y-m-d') : (string) $endsAt;
                $add  = $months > 0 ? $months : 1;
                $endsAt = date('Y-m-d', strtotime($base . ' +' . $add . ' months'));
                if (!$startsAt) $startsAt = date('Y-m-d');
                $status = 'active';
            }

            Database::run(
                'UPDATE shops SET plan_id=?, status=?, subscription_starts_at=?, subscription_ends_at=? WHERE id=?',
                [$planId, $status, $startsAt, $endsAt, $shopId]
            );
            AdminLog::write('shop_plan_' . $mode, 'shop', (string) $shopId, $shopId,
                ['plan_id' => $planId, 'status' => $status, 'starts_at' => $startsAt, 'ends_at' => $endsAt]);
            Flash::success('Plan updated. The device applies it on its next sync or login.');
            break;


        case 'user_create':
            $uname = post('username');
            $pass  = (string) ($_POST['password'] ?? '');
            if ($uname === '' || strlen($pass) < 8) {
                Flash::error('Username and a password of at least 8 characters are required.');
                break;
            }
            try {
                Database::run(
                    'INSERT INTO users (shop_id, username, password_hash, full_name, role, fingerprint_enabled)
                     VALUES (?,?,?,?,?,?)',
                    [
                        $shopId, $uname, password_hash($pass, PASSWORD_BCRYPT), post_null('full_name'),
                        pick(post('role'), ['owner', 'manager', 'cashier'], 'cashier'), post_bool('fingerprint_enabled'),
                    ]
                );
                AdminLog::write('user_created', 'user', (string) Database::insertId(), $shopId, ['username' => $uname]);
                Flash::success('Staff account created.');
            } catch (\PDOException) {
                Flash::error('That username already exists in this shop.');
            }
            break;

        case 'user_update':
            $uid = post_int('user_id');
            Database::run(
                'UPDATE users SET full_name=?, role=?, is_active=?, fingerprint_enabled=? WHERE id=? AND shop_id=?',
                [
                    post_null('full_name'), pick(post('role'), ['owner', 'manager', 'cashier'], 'cashier'),
                    post_bool('is_active'), post_bool('fingerprint_enabled'), $uid, $shopId,
                ]
            );
            $newPass = (string) ($_POST['password'] ?? '');
            if ($newPass !== '') {
                if (strlen($newPass) < 8) {
                    Flash::error('Password must be at least 8 characters.');
                    break;
                }
                Database::run('UPDATE users SET password_hash=?, failed_attempts=0, locked_until=NULL WHERE id=? AND shop_id=?',
                    [password_hash($newPass, PASSWORD_BCRYPT), $uid, $shopId]);
                Database::run('UPDATE refresh_tokens SET revoked_at = NOW() WHERE user_id = ? AND revoked_at IS NULL', [$uid]);
                AdminLog::write('user_password_reset', 'user', (string) $uid, $shopId);
            }
            AdminLog::write('user_updated', 'user', (string) $uid, $shopId);
            Flash::success('Staff account updated.');
            break;

        case 'user_unlock':
            $uid = post_int('user_id');
            Database::run('UPDATE users SET failed_attempts = 0, locked_until = NULL WHERE id=? AND shop_id=?', [$uid, $shopId]);
            AdminLog::write('user_unlocked', 'user', (string) $uid, $shopId);
            Flash::success('Account unlocked.');
            break;

        case 'user_delete':
            $uid = post_int('user_id');
            Database::run('UPDATE users SET deleted_at = NOW(), is_active = 0 WHERE id=? AND shop_id=?', [$uid, $shopId]);
            Database::run('UPDATE refresh_tokens SET revoked_at = NOW() WHERE user_id = ? AND revoked_at IS NULL', [$uid]);
            AdminLog::write('user_deleted', 'user', (string) $uid, $shopId);
            Flash::success('Staff account removed.');
            break;

        case 'device_reset':
            $uid = post_int('user_id');
            $did = post_int('device_id');
            Database::transaction(function () use ($uid, $did, $shopId) {
                if ($uid > 0) {
                    Database::run(
                        'UPDATE users SET device_id = NULL, device_name = NULL, device_bound_at = NULL WHERE id=? AND shop_id=?',
                        [$uid, $shopId]
                    );
                    Database::run('UPDATE refresh_tokens SET revoked_at = NOW() WHERE user_id = ? AND revoked_at IS NULL', [$uid]);
                }
                if ($did > 0) {
                    Database::run('UPDATE devices SET status = "reset", user_id = NULL WHERE id=? AND shop_id=?', [$did, $shopId]);
                }
            });
            AdminLog::write('device_reset', 'device', (string) $did, $shopId);
            Flash::success('Device unbound. The user can now log in from a new device.');
            break;

        case 'device_block':
            $did = post_int('device_id');
            $to  = pick(post('status'), ['active', 'blocked'], 'blocked');
            Database::run('UPDATE devices SET status = ? WHERE id=? AND shop_id=?', [$to, $did, $shopId]);
            AdminLog::write('device_' . $to, 'device', (string) $did, $shopId);
            Flash::success('Device marked as ' . $to . '.');
            break;

        case 'theme':
            $themeKey  = array_key_exists((string) post('theme_key'), $THEMES)
                ? (string) post('theme_key') : 'material_you';
            // Picking a preset publishes its full design language; the colour
            // fields stay editable for shops that need a custom brand.
            $primary   = valid_hex(post('primary_color'), $THEMES[$themeKey][1]);
            $secondary = valid_hex(post('secondary_color'), $THEMES[$themeKey][2]);
            $receipt   = (string) post('receipt_template', 'classic');
            if ($receipt === '') $receipt = 'classic';
            $exists    = Database::first('SELECT id FROM themes WHERE shop_id = ?', [$shopId]);
            if ($exists) {
                Database::run(
                    'UPDATE themes SET theme_key=?, primary_color=?, secondary_color=?, logo_url=?, splash_url=?, app_name=?, receipt_template=?, version = version + 1
                      WHERE shop_id=?',
                    [$themeKey, $primary, $secondary, post_null('logo_url'), post_null('splash_url'), post('app_name', 'QuickTap POS') ?: 'QuickTap POS', $receipt, $shopId]
                );
            } else {
                Database::run(
                    'INSERT INTO themes (shop_id, theme_key, primary_color, secondary_color, logo_url, splash_url, app_name, receipt_template)
                     VALUES (?,?,?,?,?,?,?,?)',
                    [$shopId, $themeKey, $primary, $secondary, post_null('logo_url'), post_null('splash_url'), post('app_name', 'QuickTap POS') ?: 'QuickTap POS', $receipt]
                );
            }

            AdminLog::write('theme_updated', 'theme', (string) $shopId, $shopId, ['theme' => $themeKey, 'primary' => $primary]);
            Flash::success('Theme published — devices apply it on the next config sync.');
            break;

        case 'features':
            foreach (array_keys($FEATURES) as $key) {
                Database::run(
                    'INSERT INTO feature_toggles (shop_id, feature_key, enabled) VALUES (?,?,?)
                     ON DUPLICATE KEY UPDATE enabled = VALUES(enabled)',
                    [$shopId, $key, !empty($_POST['feature'][$key]) ? 1 : 0]
                );
            }
            AdminLog::write('features_updated', 'shop', (string) $shopId, $shopId);
            Flash::success('Feature toggles saved.');
            break;

        case 'settings':
            foreach ($SETTINGS as $key => [$label, $type, $default]) {
                $value = (string) ($_POST['setting'][$key] ?? $default);
                if ($type === 'int') {
                    $value = (string) max(0, (int) $value);
                }
                Database::run(
                    'INSERT INTO app_settings (shop_id, setting_key, value, value_type) VALUES (?,?,?,?)
                     ON DUPLICATE KEY UPDATE value = VALUES(value), value_type = VALUES(value_type)',
                    [$shopId, $key, $value, $type]
                );
            }
            AdminLog::write('settings_updated', 'shop', (string) $shopId, $shopId);
            Flash::success('Shop settings saved.');
            break;
    }
    redirect(url('shop', ['id' => $shopId, 'tab' => post('tab', 'overview')]));
}

$tab   = pick(query('tab'), ['overview', 'users', 'devices', 'theme', 'features', 'sales', 'backups'], 'overview');
$plans = Database::all('SELECT id, name FROM plans WHERE is_active = 1 ORDER BY price ASC');
$users = Database::all('SELECT * FROM users WHERE shop_id = ? AND deleted_at IS NULL ORDER BY role, username', [$shopId]);
$devices = Database::all('SELECT * FROM devices WHERE shop_id = ? ORDER BY last_seen_at DESC, id DESC', [$shopId]);
$theme = Database::first('SELECT * FROM themes WHERE shop_id = ?', [$shopId]) ?: [
    'theme_key' => 'material_you',
    'primary_color' => '#6750A4', 'secondary_color' => '#7D5260', 'logo_url' => '',
    'splash_url' => '', 'app_name' => 'QuickTap POS', 'receipt_template' => 'classic', 'version' => 0,
];

/** Receipt designs the Android app can render (print/ReceiptTemplates.java). */
$RECEIPT_DESIGNS = [
    'classic' => 'Classic', 'minimal' => 'Minimal', 'compact' => 'Compact',
    'boutique' => 'Boutique', 'corporate' => 'Corporate', 'cafe' => 'Cafe',
    'retail' => 'Retail', 'wholesale' => 'Wholesale', 'elegant' => 'Elegant',
    'thermal58' => 'Thermal 58', 'thermal80' => 'Thermal 80', 'delivery' => 'Delivery',
    'kitchen' => 'Kitchen ticket', 'luxury' => 'Luxury', 'invoice' => 'Tax invoice',
];


$toggleRows = Database::all('SELECT feature_key, enabled FROM feature_toggles WHERE shop_id = ?', [$shopId]);
$toggles = [];
foreach ($toggleRows as $t) { $toggles[$t['feature_key']] = (int) $t['enabled']; }

$settingRows = Database::all('SELECT setting_key, value FROM app_settings WHERE shop_id = ?', [$shopId]);
$settings = [];
foreach ($settingRows as $s) { $settings[$s['setting_key']] = (string) $s['value']; }

$currency = (string) $shop['currency'];
$kpi = Database::first(
    'SELECT COALESCE(SUM(total),0) revenue, COUNT(*) orders
       FROM orders WHERE shop_id=? AND deleted_at IS NULL AND status="completed"
        AND ordered_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)',
    [$shopId]
) ?: ['revenue' => 0, 'orders' => 0];
$productCount  = (int) (Database::first('SELECT COUNT(*) c FROM products WHERE shop_id=? AND deleted_at IS NULL', [$shopId])['c'] ?? 0);
$customerCount = (int) (Database::first('SELECT COUNT(*) c FROM customers WHERE shop_id=? AND deleted_at IS NULL', [$shopId])['c'] ?? 0);
$lowStock = Database::all(
    'SELECT name, stock FROM products WHERE shop_id=? AND deleted_at IS NULL AND track_stock=1 AND stock <= ?
     ORDER BY stock ASC LIMIT 10',
    [$shopId, (float) ($settings['low_stock_threshold'] ?? 5)]
);
$orders = Database::all(
    'SELECT o.*, u.username FROM orders o LEFT JOIN users u ON u.id = o.user_id
      WHERE o.shop_id = ? AND o.deleted_at IS NULL ORDER BY o.ordered_at DESC LIMIT 25',
    [$shopId]
);
$backups = Database::all('SELECT * FROM backups WHERE shop_id = ? ORDER BY created_at DESC LIMIT 25', [$shopId]);

$tabs = [
    'overview' => 'Overview', 'users' => 'Staff (' . count($users) . ')',
    'devices'  => 'Devices (' . count($devices) . ')', 'theme' => 'Theme',
    'features' => 'Features & settings', 'sales' => 'Sales', 'backups' => 'Backups',
];
?>

<div class="d-flex flex-wrap align-items-center gap-3 mb-3">
  <a href="<?= e(url('shops')) ?>" class="btn btn-sm btn-outline-secondary"><i class="bi bi-arrow-left"></i></a>
  <div>
    <h2 class="h4 mb-1"><?= e($shop['name']) ?> <?= status_badge((string) $shop['status']) ?></h2>
    <div class="text-secondary small">
      UUID <?= e($shop['uuid']) ?> · created <?= e(nice_date($shop['created_at'], 'd M Y')) ?>
    </div>
  </div>
</div>

<ul class="nav nav-tabs mb-3">
  <?php foreach ($tabs as $slug => $label): ?>
    <li class="nav-item">
      <a class="nav-link<?= $tab === $slug ? ' active' : '' ?>" href="<?= e(url('shop', ['id' => $shopId, 'tab' => $slug])) ?>"><?= e($label) ?></a>
    </li>
  <?php endforeach; ?>
</ul>

<?php if ($tab === 'overview'): ?>
  <div class="row g-3 mb-3">
    <div class="col-6 col-lg-3"><div class="stat-card"><div class="label">Revenue (30d)</div><div class="value"><?= e(money($kpi['revenue'], $currency)) ?></div><div class="trend"><?= (int) $kpi['orders'] ?> orders</div></div></div>
    <div class="col-6 col-lg-3"><div class="stat-card"><div class="label">Products</div><div class="value"><?= $productCount ?></div><div class="trend">in catalog</div></div></div>
    <div class="col-6 col-lg-3"><div class="stat-card"><div class="label">Customers</div><div class="value"><?= $customerCount ?></div><div class="trend">registered</div></div></div>
    <div class="col-6 col-lg-3"><div class="stat-card"><div class="label">Active devices</div><div class="value"><?= count(array_filter($devices, fn($d) => $d['status'] === 'active')) ?></div><div class="trend">bound</div></div></div>
  </div>

  <div class="row g-3">
    <div class="col-lg-8">
      <form method="post" class="card">
        <?= Csrf::field() ?>
        <input type="hidden" name="action" value="profile"><input type="hidden" name="tab" value="overview">
        <div class="card-header">Shop profile</div>
        <div class="card-body row g-3">
          <div class="col-md-6"><label class="form-label small">Shop name</label><input name="name" class="form-control" value="<?= e($shop['name']) ?>" required></div>
          <div class="col-md-6"><label class="form-label small">Owner name</label><input name="owner_name" class="form-control" value="<?= e($shop['owner_name']) ?>"></div>
          <div class="col-md-4"><label class="form-label small">Phone</label><input name="phone" class="form-control" value="<?= e($shop['phone']) ?>"></div>
          <div class="col-md-4"><label class="form-label small">Email</label><input name="email" type="email" class="form-control" value="<?= e($shop['email']) ?>"></div>
          <div class="col-md-4"><label class="form-label small">Currency</label><input name="currency" class="form-control" value="<?= e($shop['currency']) ?>"></div>
          <div class="col-12"><label class="form-label small">Address</label><input name="address" class="form-control" value="<?= e($shop['address']) ?>"></div>
        </div>
        <div class="card-footer bg-transparent border-0 text-end pb-3 pe-3"><button class="btn btn-primary">Save changes</button></div>
      </form>

      <?php
        $planRow    = $shop['plan_id']
            ? Database::first('SELECT name, code, billing_cycle FROM plans WHERE id = ?', [(int) $shop['plan_id']])
            : null;
        $planEnds   = $shop['subscription_ends_at'];
        $daysLeft   = plan_days_left($planEnds);
        $expired    = plan_is_expired($planEnds);
        $planActive = $planRow && $shop['status'] === 'active' && !$expired;
      ?>
      <div class="card mt-3">
        <div class="card-header d-flex align-items-center">
          Plan &amp; subscription
          <span class="ms-auto">
            <?php if (!$planRow): ?><span class="badge text-bg-secondary">No plan</span>
            <?php elseif ($expired): ?><span class="badge text-bg-danger">Expired</span>
            <?php elseif ($planActive): ?><span class="badge text-bg-success">Active</span>
            <?php else: ?><span class="badge text-bg-warning">Inactive</span><?php endif; ?>
          </span>
        </div>
        <div class="card-body">
          <div class="row g-3 mb-3 small">
            <div class="col-6 col-md-3"><div class="text-secondary">Current plan</div><div class="fw-semibold"><?= e($planRow['name'] ?? '—') ?></div></div>
            <div class="col-6 col-md-3"><div class="text-secondary">Activated</div><div class="fw-semibold"><?= e(nice_date($shop['subscription_starts_at'], 'd M Y')) ?></div></div>
            <div class="col-6 col-md-3"><div class="text-secondary">Expires</div><div class="fw-semibold"><?= $planEnds ? e(nice_date($planEnds, 'd M Y')) : 'Never' ?></div></div>
            <div class="col-6 col-md-3"><div class="text-secondary">Remaining</div><div class="fw-semibold"><?= $planEnds ? ((int) $daysLeft) . ' days' : '—' ?></div></div>
          </div>

          <form method="post" class="row g-3">
            <?= Csrf::field() ?>
            <input type="hidden" name="action" value="plan"><input type="hidden" name="tab" value="overview">
            <input type="hidden" name="mode" value="save">
            <div class="col-md-4">
              <label class="form-label small">Plan</label>
              <select name="plan_id" class="form-select">
                <option value="">No plan</option>
                <?php foreach ($plans as $pl): ?>
                  <option value="<?= (int) $pl['id'] ?>" <?= (int) $shop['plan_id'] === (int) $pl['id'] ? 'selected' : '' ?>><?= e($pl['name']) ?></option>
                <?php endforeach; ?>
              </select>
            </div>
            <div class="col-md-4">
              <label class="form-label small">Plan status</label>
              <select name="plan_status" class="form-select">
                <option value="active" <?= $shop['status'] === 'active' ? 'selected' : '' ?>>Active</option>
                <option value="inactive" <?= $shop['status'] !== 'active' ? 'selected' : '' ?>>Inactive</option>
              </select>
            </div>
            <div class="col-md-4">
              <label class="form-label small">Duration</label>
              <select name="plan_duration" class="form-select">
                <option value="0">Plan default (billing cycle)</option>
                <?php foreach (plan_durations() as $m => $label): if ($m === 0) continue; ?>
                  <option value="<?= (int) $m ?>"><?= e($label) ?></option>
                <?php endforeach; ?>
              </select>
            </div>
            <div class="col-md-4"><label class="form-label small">Activated on</label><input type="date" name="subscription_starts_at" class="form-control" value="<?= e($shop['subscription_starts_at']) ?>"></div>
            <div class="col-md-4"><label class="form-label small">Expires on</label><input type="date" name="subscription_ends_at" class="form-control" value="<?= e($shop['subscription_ends_at']) ?>"></div>
            <div class="col-md-4 d-flex align-items-end"><div class="small text-secondary">Empty expiry is calculated from the duration.</div></div>
            <div class="col-12 d-flex flex-wrap gap-2">
              <button class="btn btn-primary btn-sm">Save plan</button>
              <button class="btn btn-outline-success btn-sm" name="mode" value="activate">Activate</button>
              <button class="btn btn-outline-warning btn-sm" name="mode" value="deactivate">Deactivate</button>
              <button class="btn btn-outline-secondary btn-sm" name="mode" value="renew">Renew</button>
              <button class="btn btn-outline-secondary btn-sm" name="mode" value="extend">Extend expiry</button>
            </div>
            <div class="col-12"><div class="small text-secondary">Activate / Renew / Extend use the duration selected above. Devices cannot buy or change their own plan.</div></div>
          </form>
        </div>
      </div>

    </div>
    <div class="col-lg-4">
      <div class="card h-100">
        <div class="card-header">Low stock alerts</div>
        <div class="table-responsive">
          <table class="table"><tbody>
          <?php foreach ($lowStock as $p): ?>
            <tr><td><?= e($p['name']) ?></td><td class="text-end text-warning"><?= rtrim(rtrim(number_format((float) $p['stock'], 3), '0'), '.') ?></td></tr>
          <?php endforeach; ?>
          <?php if (!$lowStock): ?><tr><td class="empty">Stock levels look healthy.</td></tr><?php endif; ?>
          </tbody></table>
        </div>
      </div>
    </div>
  </div>

<?php elseif ($tab === 'users'): ?>
  <div class="d-flex mb-3">
    <button class="btn btn-sm btn-primary ms-auto" data-bs-toggle="modal" data-bs-target="#newUser"><i class="bi bi-plus-lg"></i> Add staff</button>
  </div>
  <div class="card">
    <div class="table-responsive">
      <table class="table align-middle">
        <thead><tr><th>User</th><th>Role</th><th>Device</th><th>Biometric</th><th>Last login</th><th>Status</th><th></th></tr></thead>
        <tbody>
        <?php foreach ($users as $u): ?>
          <tr>
            <td><span class="fw-semibold"><?= e($u['username']) ?></span><div class="text-secondary small"><?= e($u['full_name'] ?: '—') ?></div></td>
            <td class="text-capitalize"><?= e($u['role']) ?></td>
            <td class="small"><?= $u['device_id'] ? e($u['device_name'] ?: 'Bound device') . '<br><span class="text-secondary">' . e(nice_date($u['device_bound_at'], 'd M Y')) . '</span>' : '<span class="text-secondary">Not bound</span>' ?></td>
            <td><?= (int) $u['fingerprint_enabled'] ? '<i class="bi bi-fingerprint text-success"></i>' : '<span class="text-secondary">off</span>' ?></td>
            <td class="small"><?= e(nice_date($u['last_login_at'])) ?></td>
            <td>
              <?php if ($u['locked_until'] && strtotime((string) $u['locked_until']) > time()): ?>
                <span class="badge text-bg-danger">Locked</span>
              <?php else: ?>
                <?= (int) $u['is_active'] ? '<span class="badge text-bg-success">Active</span>' : '<span class="badge text-bg-secondary">Disabled</span>' ?>
              <?php endif; ?>
            </td>
            <td class="text-end">
              <div class="btn-group btn-group-sm">
                <button class="btn btn-outline-secondary" data-bs-toggle="modal" data-bs-target="#editUser<?= (int) $u['id'] ?>">Edit</button>
                <button class="btn btn-outline-secondary dropdown-toggle" data-bs-toggle="dropdown"></button>
                <ul class="dropdown-menu dropdown-menu-end">
                  <li><form method="post" class="px-2 py-1" data-confirm="Unbind this user's device?">
                    <?= Csrf::field() ?><input type="hidden" name="action" value="device_reset">
                    <input type="hidden" name="tab" value="users"><input type="hidden" name="user_id" value="<?= (int) $u['id'] ?>">
                    <button class="btn btn-sm btn-link p-0 text-decoration-none">Reset device binding</button></form></li>
                  <li><form method="post" class="px-2 py-1">
                    <?= Csrf::field() ?><input type="hidden" name="action" value="user_unlock">
                    <input type="hidden" name="tab" value="users"><input type="hidden" name="user_id" value="<?= (int) $u['id'] ?>">
                    <button class="btn btn-sm btn-link p-0 text-decoration-none">Unlock account</button></form></li>
                  <li><hr class="dropdown-divider"></li>
                  <li><form method="post" class="px-2 py-1" data-confirm="Remove this staff account?">
                    <?= Csrf::field() ?><input type="hidden" name="action" value="user_delete">
                    <input type="hidden" name="tab" value="users"><input type="hidden" name="user_id" value="<?= (int) $u['id'] ?>">
                    <button class="btn btn-sm btn-link p-0 text-danger text-decoration-none">Delete user</button></form></li>
                </ul>
              </div>
            </td>
          </tr>
        <?php endforeach; ?>
        <?php if (!$users): ?><tr><td colspan="7" class="empty">No staff accounts yet.</td></tr><?php endif; ?>
        </tbody>
      </table>
    </div>
  </div>

  <?php foreach ($users as $u): ?>
    <div class="modal fade" id="editUser<?= (int) $u['id'] ?>" tabindex="-1">
      <div class="modal-dialog modal-dialog-centered">
        <form class="modal-content" method="post">
          <?= Csrf::field() ?>
          <input type="hidden" name="action" value="user_update"><input type="hidden" name="tab" value="users">
          <input type="hidden" name="user_id" value="<?= (int) $u['id'] ?>">
          <div class="modal-header border-0"><h5 class="modal-title">Edit <?= e($u['username']) ?></h5>
            <button type="button" class="btn-close" data-bs-dismiss="modal"></button></div>
          <div class="modal-body row g-3">
            <div class="col-12"><label class="form-label small">Full name</label><input name="full_name" class="form-control" value="<?= e($u['full_name']) ?>"></div>
            <div class="col-md-6">
              <label class="form-label small">Role</label>
              <select name="role" class="form-select">
                <?php foreach (['owner', 'manager', 'cashier'] as $r): ?>
                  <option value="<?= $r ?>" <?= $u['role'] === $r ? 'selected' : '' ?>><?= ucfirst($r) ?></option>
                <?php endforeach; ?>
              </select>
            </div>
            <div class="col-md-6"><label class="form-label small">New password (optional)</label><input name="password" type="text" class="form-control" minlength="8" autocomplete="new-password"></div>
            <div class="col-md-6 form-check ms-2">
              <input class="form-check-input" type="checkbox" name="is_active" value="1" id="ua<?= (int) $u['id'] ?>" <?= (int) $u['is_active'] ? 'checked' : '' ?>>
              <label class="form-check-label small" for="ua<?= (int) $u['id'] ?>">Account active</label>
            </div>
            <div class="col-md-5 form-check">
              <input class="form-check-input" type="checkbox" name="fingerprint_enabled" value="1" id="uf<?= (int) $u['id'] ?>" <?= (int) $u['fingerprint_enabled'] ? 'checked' : '' ?>>
              <label class="form-check-label small" for="uf<?= (int) $u['id'] ?>">Allow fingerprint login</label>
            </div>
          </div>
          <div class="modal-footer border-0">
            <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Cancel</button>
            <button class="btn btn-primary">Save</button>
          </div>
        </form>
      </div>
    </div>
  <?php endforeach; ?>

  <div class="modal fade" id="newUser" tabindex="-1">
    <div class="modal-dialog modal-dialog-centered">
      <form class="modal-content" method="post">
        <?= Csrf::field() ?>
        <input type="hidden" name="action" value="user_create"><input type="hidden" name="tab" value="users">
        <div class="modal-header border-0"><h5 class="modal-title">Add staff account</h5>
          <button type="button" class="btn-close" data-bs-dismiss="modal"></button></div>
        <div class="modal-body row g-3">
          <div class="col-md-6"><label class="form-label small">Username *</label><input name="username" class="form-control" required autocomplete="off"></div>
          <div class="col-md-6"><label class="form-label small">Password * (min 8)</label><input name="password" type="text" class="form-control" required minlength="8" autocomplete="new-password"></div>
          <div class="col-md-6"><label class="form-label small">Full name</label><input name="full_name" class="form-control"></div>
          <div class="col-md-6">
            <label class="form-label small">Role</label>
            <select name="role" class="form-select"><option value="cashier">Cashier</option><option value="manager">Manager</option><option value="owner">Owner</option></select>
          </div>
          <div class="col-12 form-check ms-2">
            <input class="form-check-input" type="checkbox" name="fingerprint_enabled" value="1" id="nf">
            <label class="form-check-label small" for="nf">Allow fingerprint login</label>
          </div>
        </div>
        <div class="modal-footer border-0">
          <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Cancel</button>
          <button class="btn btn-primary">Create</button>
        </div>
      </form>
    </div>
  </div>

<?php elseif ($tab === 'devices'): ?>
  <div class="card">
    <div class="table-responsive">
      <table class="table align-middle">
        <thead><tr><th>Device</th><th>Bound user</th><th>App</th><th>OS</th><th>Last seen</th><th>Status</th><th></th></tr></thead>
        <tbody>
        <?php foreach ($devices as $d):
            $owner = null;
            foreach ($users as $u) { if ((int) $u['id'] === (int) $d['user_id']) { $owner = $u; } }
        ?>
          <tr>
            <td><span class="fw-semibold"><?= e($d['device_name'] ?: 'Unnamed device') ?></span>
              <div class="text-secondary small font-monospace"><?= e(substr((string) $d['device_id'], 0, 16)) ?>…</div></td>
            <td><?= $owner ? e($owner['username']) : '<span class="text-secondary">—</span>' ?></td>
            <td class="small"><?= e($d['app_version'] ?: '—') ?></td>
            <td class="small"><?= e($d['os_version'] ?: '—') ?></td>
            <td class="small"><?= e(nice_date($d['last_seen_at'])) ?></td>
            <td><?= status_badge((string) $d['status']) ?></td>
            <td class="text-end">
              <form method="post" class="d-inline" data-confirm="Unbind this device?">
                <?= Csrf::field() ?><input type="hidden" name="action" value="device_reset">
                <input type="hidden" name="tab" value="devices"><input type="hidden" name="device_id" value="<?= (int) $d['id'] ?>">
                <input type="hidden" name="user_id" value="<?= (int) $d['user_id'] ?>">
                <button class="btn btn-sm btn-outline-secondary">Reset</button>
              </form>
              <form method="post" class="d-inline">
                <?= Csrf::field() ?><input type="hidden" name="action" value="device_block">
                <input type="hidden" name="tab" value="devices"><input type="hidden" name="device_id" value="<?= (int) $d['id'] ?>">
                <input type="hidden" name="status" value="<?= $d['status'] === 'blocked' ? 'active' : 'blocked' ?>">
                <button class="btn btn-sm btn-outline-<?= $d['status'] === 'blocked' ? 'success' : 'danger' ?>">
                  <?= $d['status'] === 'blocked' ? 'Unblock' : 'Block' ?>
                </button>
              </form>
            </td>
          </tr>
        <?php endforeach; ?>
        <?php if (!$devices): ?><tr><td colspan="7" class="empty">No devices have connected yet.</td></tr><?php endif; ?>
        </tbody>
      </table>
    </div>
  </div>

<?php elseif ($tab === 'theme'): ?>
  <div class="row g-3">
    <div class="col-lg-7">
      <form method="post" class="card">
        <?= Csrf::field() ?>
        <input type="hidden" name="action" value="theme"><input type="hidden" name="tab" value="theme">
        <div class="card-header">Server-driven theme (version <?= (int) $theme['version'] ?>)</div>
        <div class="card-body row g-3">
          <div class="col-12">
            <label class="form-label small">Premium theme (Super Admin only)</label>
            <select name="theme_key" class="form-select">
              <?php foreach ($THEMES as $tk => $tv): ?>
                <option value="<?= e($tk) ?>" <?= (($theme['theme_key'] ?? 'material_you') === $tk) ? 'selected' : '' ?>><?= e($tv[0]) ?></option>
              <?php endforeach; ?>
            </select>
            <div class="form-text">Switches the complete application design on every device — colours, cards, typography, navigation, dialogs and charts. Functionality is unchanged.</div>
          </div>
          <div class="col-md-6">
            <label class="form-label small">Primary colour</label>
            <input type="color" name="primary_color" class="form-control form-control-color" data-preview="#previewPrimary" value="<?= e($theme['primary_color']) ?>">
          </div>
          <div class="col-md-6">
            <label class="form-label small">Secondary colour</label>
            <input type="color" name="secondary_color" class="form-control form-control-color" data-preview="#previewSecondary" value="<?= e($theme['secondary_color']) ?>">
          </div>
          <div class="col-12"><label class="form-label small">App name shown on device</label><input name="app_name" class="form-control" value="<?= e($theme['app_name']) ?>"></div>
          <div class="col-md-6"><label class="form-label small">Logo URL</label><input name="logo_url" class="form-control" value="<?= e($theme['logo_url']) ?>"></div>
          <div class="col-md-6"><label class="form-label small">Splash image URL</label><input name="splash_url" class="form-control" value="<?= e($theme['splash_url']) ?>"></div>
          <div class="col-md-6">
            <label class="form-label small">Receipt design</label>
            <select name="receipt_template" class="form-select">
              <?php foreach ($RECEIPT_DESIGNS as $rk => $rv): ?>
                <option value="<?= e($rk) ?>" <?= (($theme['receipt_template'] ?? 'classic') === $rk) ? 'selected' : '' ?>><?= e($rv) ?></option>
              <?php endforeach; ?>
            </select>
            <div class="form-text">Full previews &amp; per-shop picker: <a href="?page=receipts&amp;shop_id=<?= (int) $shopId ?>">Receipt designs</a>.</div>
          </div>
          <div class="col-md-6 d-flex align-items-end">
            <a class="btn btn-outline-secondary w-100" href="?page=splash&amp;shop_id=<?= (int) $shopId ?>">Splash screen settings</a>
          </div>

        </div>
        <div class="card-footer bg-transparent border-0 d-flex justify-content-between align-items-center pb-3 px-3">
          <span class="small text-secondary">Saving bumps the theme version so devices refresh on next sync.</span>
          <button class="btn btn-primary">Publish theme</button>
        </div>
      </form>
    </div>
    <div class="col-lg-5">
      <div class="card h-100">
        <div class="card-header">Live preview</div>
        <div class="card-body">
          <div class="rounded-4 p-3" style="background:#0B0F14;border:1px solid var(--qt-border)">
            <div id="previewPrimary" class="rounded-3 p-3 mb-2" style="background:<?= e($theme['primary_color']) ?>">
              <strong class="text-dark"><?= e($theme['app_name']) ?></strong>
            </div>
            <div class="d-flex gap-2">
              <div id="previewSecondary" class="rounded-3 flex-fill" style="height:44px;background:<?= e($theme['secondary_color']) ?>"></div>
              <div class="rounded-3 flex-fill" style="height:44px;background:var(--qt-surface-2)"></div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>

<?php elseif ($tab === 'features'): ?>
  <div class="row g-3">
    <div class="col-lg-6">
      <form method="post" class="card h-100">
        <?= Csrf::field() ?>
        <input type="hidden" name="action" value="features"><input type="hidden" name="tab" value="features">
        <div class="card-header">Feature toggles</div>
        <div class="card-body">
          <?php foreach ($FEATURES as $key => $label):
              $on = array_key_exists($key, $toggles) ? $toggles[$key] : 1; ?>
            <div class="form-check form-switch mb-2">
              <input class="form-check-input" type="checkbox" role="switch" id="f_<?= e($key) ?>"
                     name="feature[<?= e($key) ?>]" value="1" <?= $on ? 'checked' : '' ?>>
              <label class="form-check-label" for="f_<?= e($key) ?>"><?= e($label) ?></label>
            </div>
          <?php endforeach; ?>
        </div>
        <div class="card-footer bg-transparent border-0 text-end pb-3 pe-3"><button class="btn btn-primary">Save toggles</button></div>
      </form>
    </div>
    <div class="col-lg-6">
      <form method="post" class="card h-100">
        <?= Csrf::field() ?>
        <input type="hidden" name="action" value="settings"><input type="hidden" name="tab" value="features">
        <div class="card-header">App settings pushed to devices</div>
        <div class="card-body row g-3">
          <?php foreach ($SETTINGS as $key => [$label, $type, $default]): ?>
            <div class="col-md-6">
              <label class="form-label small"><?= e($label) ?></label>
              <input class="form-control" name="setting[<?= e($key) ?>]"
                     type="<?= $type === 'int' ? 'number' : 'text' ?>"
                     value="<?= e($settings[$key] ?? $default) ?>">
            </div>
          <?php endforeach; ?>
        </div>
        <div class="card-footer bg-transparent border-0 text-end pb-3 pe-3"><button class="btn btn-primary">Save settings</button></div>
      </form>
    </div>
  </div>

<?php elseif ($tab === 'sales'): ?>
  <div class="card">
    <div class="card-header">Latest 25 orders</div>
    <div class="table-responsive">
      <table class="table align-middle">
        <thead><tr><th>Invoice</th><th>Cashier</th><th>Payment</th><th>Date</th><th>Status</th><th class="text-end">Total</th></tr></thead>
        <tbody>
        <?php foreach ($orders as $o): ?>
          <tr>
            <td class="font-monospace small"><?= e($o['invoice_no']) ?></td>
            <td><?= e($o['username'] ?: '—') ?></td>
            <td class="text-capitalize small"><?= e($o['payment_method']) ?></td>
            <td class="small"><?= e(nice_date($o['ordered_at'])) ?></td>
            <td><?= status_badge((string) $o['status']) ?></td>
            <td class="text-end fw-semibold"><?= e(money($o['total'], $currency)) ?></td>
          </tr>
        <?php endforeach; ?>
        <?php if (!$orders): ?><tr><td colspan="6" class="empty">No sales synced yet.</td></tr><?php endif; ?>
        </tbody>
      </table>
    </div>
  </div>

<?php else: ?>
  <div class="card">
    <div class="card-header">Google Drive backups</div>
    <div class="table-responsive">
      <table class="table align-middle">
        <thead><tr><th>File</th><th>Kind</th><th>Provider</th><th>Size</th><th>Encrypted</th><th>Created</th></tr></thead>
        <tbody>
        <?php foreach ($backups as $b): ?>
          <tr>
            <td><span class="fw-semibold"><?= e($b['file_name']) ?></span>
              <div class="text-secondary small font-monospace"><?= e($b['file_id'] ?: '—') ?></div></td>
            <td class="text-capitalize"><?= e($b['kind']) ?></td>
            <td class="text-uppercase small"><?= e($b['provider']) ?></td>
            <td class="small"><?= number_format(((int) $b['size_bytes']) / 1024, 1) ?> KB</td>
            <td><?= (int) $b['encrypted'] ? '<i class="bi bi-shield-lock text-success"></i>' : '<span class="text-warning">No</span>' ?></td>
            <td class="small"><?= e(nice_date($b['created_at'])) ?></td>
          </tr>
        <?php endforeach; ?>
        <?php if (!$backups): ?><tr><td colspan="6" class="empty">No backups registered for this shop.</td></tr><?php endif; ?>
        </tbody>
      </table>
    </div>
  </div>
<?php endif; ?>
