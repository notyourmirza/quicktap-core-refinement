<?php
/** App versions & force-update control. */

use Admin\AdminLog;
use Admin\Csrf;
use Admin\Flash;
use QuickTap\Core\Database;

if (is_post()) {
    $action = post('action');

    if ($action === 'create') {
        $code = post_int('version_code');
        $name = post('version_name');
        if ($code <= 0 || $name === '') {
            Flash::error('Version code and version name are required.');
            redirect(url('versions'));
        }
        try {
            Database::run(
                'INSERT INTO app_versions (version_code, version_name, min_supported_code, force_update, changelog, download_url)
                 VALUES (?,?,?,?,?,?)',
                [$code, $name, max(1, post_int('min_supported_code', 1)), post_bool('force_update'),
                 post_null('changelog'), post_null('download_url')]
            );
            AdminLog::write('app_version_published', 'app_version', (string) $code);
            Flash::success('Version ' . $name . ' published.');
        } catch (\PDOException) {
            Flash::error('That version code already exists.');
        }
    } elseif ($action === 'toggle_force') {
        $id = post_int('id');
        Database::run('UPDATE app_versions SET force_update = 1 - force_update WHERE id = ?', [$id]);
        AdminLog::write('app_version_force_toggled', 'app_version', (string) $id);
        Flash::success('Force-update flag toggled.');
    } elseif ($action === 'delete') {
        $id = post_int('id');
        Database::run('DELETE FROM app_versions WHERE id = ?', [$id]);
        AdminLog::write('app_version_deleted', 'app_version', (string) $id);
        Flash::success('Version removed.');
    }
    redirect(url('versions'));
}

$versions = Database::all('SELECT * FROM app_versions ORDER BY version_code DESC');
$installs = Database::all(
    'SELECT app_version, COUNT(*) c FROM devices WHERE app_version IS NOT NULL GROUP BY app_version ORDER BY c DESC LIMIT 8'
);
?>

<div class="row g-3">
  <div class="col-lg-8">
    <div class="card">
      <div class="card-header d-flex align-items-center">Released versions
        <button class="btn btn-sm btn-primary ms-auto" data-bs-toggle="modal" data-bs-target="#newVersion"><i class="bi bi-plus-lg"></i> Publish version</button>
      </div>
      <div class="table-responsive">
        <table class="table align-middle">
          <thead><tr><th>Version</th><th>Code</th><th>Min supported</th><th>Force update</th><th>Released</th><th></th></tr></thead>
          <tbody>
          <?php foreach ($versions as $v): ?>
            <tr>
              <td><span class="fw-semibold"><?= e($v['version_name']) ?></span>
                <?php if ($v['changelog']): ?><div class="text-secondary small"><?= e(mb_strimwidth((string) $v['changelog'], 0, 80, '…')) ?></div><?php endif; ?></td>
              <td><?= (int) $v['version_code'] ?></td>
              <td><?= (int) $v['min_supported_code'] ?></td>
              <td><?= (int) $v['force_update'] ? '<span class="badge text-bg-danger">Forced</span>' : '<span class="badge text-bg-secondary">Optional</span>' ?></td>
              <td class="small"><?= e(nice_date($v['released_at'], 'd M Y')) ?></td>
              <td class="text-end">
                <form method="post" class="d-inline">
                  <?= Csrf::field() ?><input type="hidden" name="action" value="toggle_force"><input type="hidden" name="id" value="<?= (int) $v['id'] ?>">
                  <button class="btn btn-sm btn-outline-secondary">Toggle force</button>
                </form>
                <form method="post" class="d-inline" data-confirm="Delete this version entry?">
                  <?= Csrf::field() ?><input type="hidden" name="action" value="delete"><input type="hidden" name="id" value="<?= (int) $v['id'] ?>">
                  <button class="btn btn-sm btn-outline-danger">Delete</button>
                </form>
              </td>
            </tr>
          <?php endforeach; ?>
          <?php if (!$versions): ?><tr><td colspan="6" class="empty">No versions published yet.</td></tr><?php endif; ?>
          </tbody>
        </table>
      </div>
    </div>
  </div>

  <div class="col-lg-4">
    <div class="card h-100">
      <div class="card-header">Installed versions in the field</div>
      <div class="table-responsive">
        <table class="table"><tbody>
        <?php foreach ($installs as $i): ?>
          <tr><td><?= e($i['app_version']) ?></td><td class="text-end"><?= (int) $i['c'] ?> devices</td></tr>
        <?php endforeach; ?>
        <?php if (!$installs): ?><tr><td class="empty">No devices reporting a version.</td></tr><?php endif; ?>
        </tbody></table>
      </div>
    </div>
  </div>
</div>

<div class="modal fade" id="newVersion" tabindex="-1">
  <div class="modal-dialog modal-dialog-centered">
    <form class="modal-content" method="post">
      <?= Csrf::field() ?>
      <input type="hidden" name="action" value="create">
      <div class="modal-header border-0"><h5 class="modal-title">Publish app version</h5>
        <button type="button" class="btn-close" data-bs-dismiss="modal"></button></div>
      <div class="modal-body row g-3">
        <div class="col-md-6"><label class="form-label small">Version code *</label><input name="version_code" type="number" class="form-control" required></div>
        <div class="col-md-6"><label class="form-label small">Version name *</label><input name="version_name" class="form-control" placeholder="2.0.0" required></div>
        <div class="col-md-6"><label class="form-label small">Minimum supported code</label><input name="min_supported_code" type="number" class="form-control" value="1"></div>
        <div class="col-md-6 d-flex align-items-end">
          <div class="form-check">
            <input class="form-check-input" type="checkbox" name="force_update" value="1" id="fu">
            <label class="form-check-label small" for="fu">Force update</label>
          </div>
        </div>
        <div class="col-12"><label class="form-label small">Download URL</label><input name="download_url" class="form-control" placeholder="https://…/quicktap.apk"></div>
        <div class="col-12"><label class="form-label small">Changelog</label><textarea name="changelog" class="form-control" rows="4"></textarea></div>
      </div>
      <div class="modal-footer border-0">
        <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Cancel</button>
        <button class="btn btn-primary">Publish</button>
      </div>
    </form>
  </div>
</div>
