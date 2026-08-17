<?php
/** API client credentials — issue, rotate and revoke app keys. */

use Admin\AdminLog;
use Admin\Csrf;
use Admin\Flash;
use Admin\Session;
use QuickTap\Core\Database;

if (is_post()) {
    $action = post('action');

    if ($action === 'create' || $action === 'rotate') {
        $apiKey    = bin2hex(random_bytes(24));
        $secretKey = bin2hex(random_bytes(32));

        if ($action === 'create') {
            $appId = preg_replace('/[^a-zA-Z0-9_.\-]/', '', post('app_id')) ?? '';
            $name  = post('name');
            if ($appId === '' || $name === '') {
                Flash::error('App ID and name are required.');
                redirect(url('api_clients'));
            }
            try {
                Database::run(
                    'INSERT INTO api_clients (app_id, name, api_key_hash, secret_hash) VALUES (?,?,?,?)',
                    [$appId, $name, hash('sha256', $apiKey), hash('sha256', $secretKey)]
                );
                AdminLog::write('api_client_created', 'api_client', $appId);
            } catch (\PDOException) {
                Flash::error('That App ID already exists.');
                redirect(url('api_clients'));
            }
        } else {
            $id  = post_int('id');
            $row = Database::first('SELECT app_id FROM api_clients WHERE id = ?', [$id]);
            if (!$row) {
                Flash::error('Client not found.');
                redirect(url('api_clients'));
            }
            Database::run('UPDATE api_clients SET api_key_hash = ?, secret_hash = ? WHERE id = ?',
                [hash('sha256', $apiKey), hash('sha256', $secretKey), $id]);
            $appId = (string) $row['app_id'];
            AdminLog::write('api_client_rotated', 'api_client', $appId);
        }

        // Shown exactly once — only hashes are persisted.
        Session::set('new_credentials', ['app_id' => $appId, 'api_key' => $apiKey, 'secret_key' => $secretKey]);
        Flash::success('Credentials generated. Copy them now — they are not stored in readable form.');
    } elseif ($action === 'toggle') {
        Database::run('UPDATE api_clients SET is_active = 1 - is_active WHERE id = ?', [post_int('id')]);
        AdminLog::write('api_client_toggled', 'api_client', (string) post_int('id'));
        Flash::success('Client status changed.');
    } elseif ($action === 'delete') {
        Database::run('DELETE FROM api_clients WHERE id = ?', [post_int('id')]);
        AdminLog::write('api_client_deleted', 'api_client', (string) post_int('id'));
        Flash::success('Client revoked.');
    }
    redirect(url('api_clients'));
}

$fresh = Session::get('new_credentials');
Session::forget('new_credentials');
$clients = Database::all('SELECT * FROM api_clients ORDER BY created_at DESC');
?>

<?php if (is_array($fresh)): ?>
  <div class="card mb-3 border-success">
    <div class="card-header text-success">New credentials for <?= e($fresh['app_id']) ?> — shown once</div>
    <div class="card-body">
      <div class="mb-2"><label class="form-label small">X-Api-Key</label>
        <input class="form-control font-monospace" readonly value="<?= e($fresh['api_key']) ?>" onclick="this.select()"></div>
      <div><label class="form-label small">Secret key (used for request signing)</label>
        <input class="form-control font-monospace" readonly value="<?= e($fresh['secret_key']) ?>" onclick="this.select()"></div>
    </div>
  </div>
<?php endif; ?>

<div class="d-flex mb-3">
  <button class="btn btn-sm btn-primary ms-auto" data-bs-toggle="modal" data-bs-target="#newClient"><i class="bi bi-plus-lg"></i> Issue credentials</button>
</div>

<div class="card">
  <div class="table-responsive">
    <table class="table align-middle">
      <thead><tr><th>Client</th><th>App ID</th><th>Key fingerprint</th><th>Created</th><th>Status</th><th></th></tr></thead>
      <tbody>
      <?php foreach ($clients as $c): ?>
        <tr>
          <td class="fw-semibold"><?= e($c['name']) ?></td>
          <td class="font-monospace small"><?= e($c['app_id']) ?></td>
          <td class="font-monospace small text-secondary"><?= e(substr((string) $c['api_key_hash'], 0, 16)) ?>…</td>
          <td class="small"><?= e(nice_date($c['created_at'], 'd M Y')) ?></td>
          <td><?= (int) $c['is_active'] ? '<span class="badge text-bg-success">Active</span>' : '<span class="badge text-bg-secondary">Disabled</span>' ?></td>
          <td class="text-end">
            <form method="post" class="d-inline" data-confirm="Rotate keys? The current keys stop working immediately.">
              <?= Csrf::field() ?><input type="hidden" name="action" value="rotate"><input type="hidden" name="id" value="<?= (int) $c['id'] ?>">
              <button class="btn btn-sm btn-outline-secondary">Rotate</button>
            </form>
            <form method="post" class="d-inline">
              <?= Csrf::field() ?><input type="hidden" name="action" value="toggle"><input type="hidden" name="id" value="<?= (int) $c['id'] ?>">
              <button class="btn btn-sm btn-outline-secondary"><?= (int) $c['is_active'] ? 'Disable' : 'Enable' ?></button>
            </form>
            <form method="post" class="d-inline" data-confirm="Permanently revoke this client?">
              <?= Csrf::field() ?><input type="hidden" name="action" value="delete"><input type="hidden" name="id" value="<?= (int) $c['id'] ?>">
              <button class="btn btn-sm btn-outline-danger">Revoke</button>
            </form>
          </td>
        </tr>
      <?php endforeach; ?>
      <?php if (!$clients): ?><tr><td colspan="6" class="empty">No API clients issued yet.</td></tr><?php endif; ?>
      </tbody>
    </table>
  </div>
</div>

<div class="modal fade" id="newClient" tabindex="-1">
  <div class="modal-dialog modal-dialog-centered">
    <form class="modal-content" method="post">
      <?= Csrf::field() ?>
      <input type="hidden" name="action" value="create">
      <div class="modal-header border-0"><h5 class="modal-title">Issue API credentials</h5>
        <button type="button" class="btn-close" data-bs-dismiss="modal"></button></div>
      <div class="modal-body row g-3">
        <div class="col-12"><label class="form-label small">App ID *</label><input name="app_id" class="form-control" placeholder="com.quicktap.pos" required></div>
        <div class="col-12"><label class="form-label small">Display name *</label><input name="name" class="form-control" placeholder="QuickTap Android App" required></div>
        <p class="small text-secondary mb-0">Only SHA-256 hashes are stored. The plain key and secret are displayed once after creation.</p>
      </div>
      <div class="modal-footer border-0">
        <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Cancel</button>
        <button class="btn btn-primary">Generate</button>
      </div>
    </form>
  </div>
</div>
