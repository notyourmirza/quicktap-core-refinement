<?php
/** Subscription plans CRUD. */

use Admin\AdminLog;
use Admin\Csrf;
use Admin\Flash;
use QuickTap\Core\Database;

// The extended marketing columns are part of the consolidated schema.sql. The
// page keeps working (without them) on older databases that predate them.
$extended = has_column('plans', 'tagline') && has_column('plans', 'price_yearly');

if (is_post()) {
    $action = post('action');
    $id     = post_int('id');

    if ($action === 'save') {
        $code = strtolower(preg_replace('/[^a-z0-9_\-]/i', '', post('code')) ?? '');
        $name = post('name');
        if ($code === '' || $name === '') {
            Flash::error('Plan code and name are required.');
            redirect(url('plans'));
        }
        $features = array_values(array_filter(array_map(
            'trim',
            preg_split('/\r\n|\r|\n/', (string) post('features', '')) ?: []
        ), static fn ($line) => $line !== ''));

        $columns = ['code', 'name', 'price', 'billing_cycle', 'max_devices', 'max_users',
                    'max_products', 'features_json', 'is_active'];
        $args    = [
            $code, $name, post_float('price'),
            pick(post('billing_cycle'), ['monthly', 'yearly', 'lifetime'], 'monthly'),
            max(1, post_int('max_devices', 1)), max(1, post_int('max_users', 1)),
            max(1, post_int('max_products', 100)),
            json_encode($features, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES),
            post_bool('is_active'),
        ];
        if ($extended) {
            array_push($columns, 'tagline', 'tag', 'price_yearly', 'sort_order');
            array_push($args, post('tagline'), post_null('tag'), post_float('price_yearly'), post_int('sort_order'));
        }

        try {
            if ($id > 0) {
                $set = implode(', ', array_map(static fn ($c) => $c . '=?', $columns));
                Database::run("UPDATE plans SET {$set} WHERE id=?", [...$args, $id]);
                AdminLog::write('plan_updated', 'plan', (string) $id);
            } else {
                $names  = implode(', ', $columns);
                $holder = implode(',', array_fill(0, count($columns), '?'));
                Database::run("INSERT INTO plans ({$names}) VALUES ({$holder})", $args);
                AdminLog::write('plan_created', 'plan', (string) Database::insertId());
            }
            Flash::success('Plan saved. Devices pick it up on the next plan-store open.');
        } catch (\PDOException $e) {
            Flash::error('Could not save the plan: ' . $e->getMessage());
        }
    } elseif ($action === 'delete' && $id > 0) {
        Database::run('UPDATE plans SET is_active = 0 WHERE id = ?', [$id]);
        AdminLog::write('plan_disabled', 'plan', (string) $id);
        Flash::success('Plan disabled.');
    }
    redirect(url('plans'));
}

$order = $extended ? 'p.sort_order ASC, p.price ASC' : 'p.price ASC';
$plans = Database::all(
    'SELECT p.*, (SELECT COUNT(*) FROM shops s WHERE s.plan_id = p.id AND s.deleted_at IS NULL) shops_count
       FROM plans p ORDER BY ' . $order
);
?>

<?php if (!$extended): ?>
  <div class="alert alert-warning">Re-run <code>server/sql/schema.sql</code> to unlock
    the in-app store fields (tagline, badge, yearly price, display order).</div>
<?php endif; ?>

<div class="d-flex mb-3">
  <button class="btn btn-sm btn-primary ms-auto" data-bs-toggle="modal" data-bs-target="#planModal"
          onclick="document.getElementById('planForm').reset();document.getElementById('planId').value='';">
    <i class="bi bi-plus-lg"></i> New plan
  </button>
</div>

<div class="card">
  <div class="table-responsive">
    <table class="table align-middle">
      <thead><tr><th>Plan</th><th>Price</th><th>Cycle</th><th>Devices</th><th>Users</th><th>Products</th><th>Shops</th><th>Status</th><th></th></tr></thead>
      <tbody>
      <?php foreach ($plans as $p): ?>
        <tr>
          <td><span class="fw-semibold"><?= e($p['name']) ?></span><div class="text-secondary small font-monospace"><?= e($p['code']) ?></div></td>
          <td><?= e(money($p['price'])) ?>
            <?php if (!empty($p['price_yearly'])): ?><div class="small text-secondary"><?= e(money($p['price_yearly'])) ?> / yr</div><?php endif; ?>
          </td>
          <td class="text-capitalize"><?= e($p['billing_cycle']) ?></td>
          <td><?= (int) $p['max_devices'] ?></td>
          <td><?= (int) $p['max_users'] ?></td>
          <td><?= (int) $p['max_products'] ?></td>
          <td><?= (int) $p['shops_count'] ?></td>
          <td><?= (int) $p['is_active'] ? '<span class="badge text-bg-success">Active</span>' : '<span class="badge text-bg-secondary">Disabled</span>' ?></td>
          <td class="text-end">
            <button class="btn btn-sm btn-outline-secondary"
                    data-bs-toggle="modal" data-bs-target="#planModal"
                    onclick='fillPlan(<?= json_encode([
                        "id" => (int) $p["id"], "code" => $p["code"], "name" => $p["name"],
                        "price" => (string) $p["price"], "billing_cycle" => $p["billing_cycle"],
                        "max_devices" => (int) $p["max_devices"], "max_users" => (int) $p["max_users"],
                        "max_products" => (int) $p["max_products"], "is_active" => (int) $p["is_active"],
                        "tagline" => $p["tagline"] ?? "", "tag" => $p["tag"] ?? "",
                        "price_yearly" => (string) ($p["price_yearly"] ?? 0), "sort_order" => (int) ($p["sort_order"] ?? 0),
                        "features" => implode("\n", (array) (json_decode((string) ($p["features_json"] ?? "[]"), true) ?: [])),
                    ], JSON_HEX_APOS | JSON_HEX_QUOT) ?>)'>Edit</button>
            <form method="post" class="d-inline" data-confirm="Disable this plan?">
              <?= Csrf::field() ?><input type="hidden" name="action" value="delete"><input type="hidden" name="id" value="<?= (int) $p['id'] ?>">
              <button class="btn btn-sm btn-outline-danger">Disable</button>
            </form>
          </td>
        </tr>
      <?php endforeach; ?>
      <?php if (!$plans): ?><tr><td colspan="9" class="empty">No plans defined.</td></tr><?php endif; ?>
      </tbody>
    </table>
  </div>
</div>

<div class="modal fade" id="planModal" tabindex="-1">
  <div class="modal-dialog modal-dialog-centered">
    <form class="modal-content" method="post" id="planForm">
      <?= Csrf::field() ?>
      <input type="hidden" name="action" value="save"><input type="hidden" name="id" id="planId">
      <div class="modal-header border-0"><h5 class="modal-title">Plan</h5>
        <button type="button" class="btn-close" data-bs-dismiss="modal"></button></div>
      <div class="modal-body row g-3">
        <div class="col-md-6"><label class="form-label small">Code *</label><input name="code" id="planCode" class="form-control" required></div>
        <div class="col-md-6"><label class="form-label small">Name *</label><input name="name" id="planName" class="form-control" required></div>
        <div class="col-12"><label class="form-label small">Tagline (shown under the plan name in the app)</label><input name="tagline" id="planTagline" class="form-control" placeholder="For a single counter finding its rhythm."></div>
        <div class="col-md-6"><label class="form-label small">Badge</label><input name="tag" id="planTag" class="form-control" placeholder="MOST POPULAR"></div>
        <div class="col-md-6"><label class="form-label small">Display order</label><input name="sort_order" id="planOrder" type="number" class="form-control" value="0"></div>
        <div class="col-md-6"><label class="form-label small">Monthly price</label><input name="price" id="planPrice" type="number" step="0.01" class="form-control" value="0"></div>
        <div class="col-md-6"><label class="form-label small">Yearly price</label><input name="price_yearly" id="planPriceYearly" type="number" step="0.01" class="form-control" value="0"></div>
        <div class="col-12"><label class="form-label small">Features (one per line)</label><textarea name="features" id="planFeatures" class="form-control" rows="5" placeholder="1 billing counter&#10;Bluetooth thermal printing"></textarea></div>
        <div class="col-md-6">
          <label class="form-label small">Billing cycle</label>
          <select name="billing_cycle" id="planCycle" class="form-select">
            <option value="monthly">Monthly</option><option value="yearly">Yearly</option><option value="lifetime">Lifetime</option>
          </select>
        </div>
        <div class="col-4"><label class="form-label small">Max devices</label><input name="max_devices" id="planDevices" type="number" class="form-control" value="1"></div>
        <div class="col-4"><label class="form-label small">Max users</label><input name="max_users" id="planUsers" type="number" class="form-control" value="3"></div>
        <div class="col-4"><label class="form-label small">Max products</label><input name="max_products" id="planProducts" type="number" class="form-control" value="1000"></div>
        <div class="col-12 form-check ms-2">
          <input class="form-check-input" type="checkbox" name="is_active" value="1" id="planActive" checked>
          <label class="form-check-label small" for="planActive">Plan is active</label>
        </div>
      </div>
      <div class="modal-footer border-0">
        <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Cancel</button>
        <button class="btn btn-primary">Save plan</button>
      </div>
    </form>
  </div>
</div>

<script>
function fillPlan(p) {
  document.getElementById('planId').value = p.id;
  document.getElementById('planCode').value = p.code;
  document.getElementById('planName').value = p.name;
  document.getElementById('planPrice').value = p.price;
  document.getElementById('planCycle').value = p.billing_cycle;
  document.getElementById('planDevices').value = p.max_devices;
  document.getElementById('planUsers').value = p.max_users;
  document.getElementById('planProducts').value = p.max_products;
  document.getElementById('planTagline').value = p.tagline || '';
  document.getElementById('planTag').value = p.tag || '';
  document.getElementById('planPriceYearly').value = p.price_yearly || 0;
  document.getElementById('planOrder').value = p.sort_order || 0;
  document.getElementById('planFeatures').value = p.features || '';
  document.getElementById('planActive').checked = !!p.is_active;
}
</script>
