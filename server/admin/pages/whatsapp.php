<?php
/** Support WhatsApp number published to every device (app_settings.support_whatsapp). */

use Admin\AdminLog;
use Admin\Csrf;
use Admin\Flash;
use QuickTap\Core\Database;

if (is_post() && post('action') === 'save') {
    $digits = preg_replace('/[^0-9]/', '', (string) post('whatsapp')) ?? '';
    if (strlen($digits) < 8) {
        Flash::error('Enter the full number in international format, e.g. 923001234567.');
        redirect(url('whatsapp'));
    }
    Database::run(
        "INSERT INTO app_settings (shop_id, setting_key, value, value_type) VALUES (NULL,'support_whatsapp',?, 'string')
         ON DUPLICATE KEY UPDATE value = VALUES(value), value_type = 'string'",
        [$digits]
    );
    AdminLog::write('support_whatsapp_updated', 'settings', $digits);
    Flash::success('WhatsApp number updated. Devices apply it on the next theme refresh.');
    redirect(url('whatsapp'));
}

$row     = Database::first("SELECT value FROM app_settings WHERE shop_id IS NULL AND setting_key = 'support_whatsapp'");
$current = (string) ($row['value'] ?? '');
?>

<div class="row g-3">
  <div class="col-lg-6">
    <form method="post" class="card">
      <?= Csrf::field() ?>
      <input type="hidden" name="action" value="save">
      <div class="card-header">Support WhatsApp number</div>
      <div class="card-body">
        <label class="form-label small">International format, digits only (no + sign)</label>
        <input name="whatsapp" class="form-control" value="<?= e($current) ?>" placeholder="923001234567" required>
        <div class="form-text">Every WhatsApp button in the app — contact admin, licence help and store
          orders — opens this number.</div>
      </div>
      <div class="card-footer"><button class="btn btn-primary">Save number</button></div>
    </form>
  </div>
  <div class="col-lg-6">
    <div class="card">
      <div class="card-header">Current link</div>
      <div class="card-body">
        <?php if ($current !== ''): ?>
          <a class="btn btn-outline-light" target="_blank" rel="noopener"
             href="https://wa.me/<?= e($current) ?>">Open wa.me/<?= e($current) ?></a>
        <?php else: ?>
          <div class="text-secondary">No number configured yet — the app falls back to its built-in default.</div>
        <?php endif; ?>
      </div>
    </div>
  </div>
</div>
