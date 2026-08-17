<?php
/** Receipt design picker — global default (themes shop_id NULL) + per-shop selection. */

use Admin\AdminLog;
use Admin\Csrf;
use Admin\Flash;
use QuickTap\Core\Database;

$DESIGNS = [
    'classic'   => 'Classic — bold double-line borders, timeless layout.',
    'minimal'   => 'Minimal — clean spacing, no borders, ultra light.',
    'compact'   => 'Compact — tight lines, saves paper on small printers.',
    'boutique'  => 'Boutique — decorative stars, centered branding.',
    'corporate' => 'Corporate — formal double rules, tax-ready layout.',
    'cafe'      => 'Cafe — friendly dashed dividers, item highlights.',
    'retail'    => 'Retail — barcode-friendly, itemised totals.',
    'wholesale' => 'Wholesale — bulk quantity emphasis, ledger style.',
    'elegant'   => 'Elegant — refined spacing with decorative accents.',
    'thermal58' => 'Thermal 58mm — optimised narrow paper width.',
    'thermal80' => 'Thermal 80mm — optimised wide paper width.',
    'delivery'  => 'Delivery — address & rider details emphasised.',
    'kitchen'   => 'Kitchen ticket — no prices, prep-focused.',
    'luxury'    => 'Luxury — premium borders and generous spacing.',
    'invoice'   => 'Tax invoice — formal GST/tax breakdown layout.',
];

$shopId = query_int('shop_id'); // 0 = global default
$shops  = Database::all('SELECT id, name FROM shops WHERE deleted_at IS NULL ORDER BY name');

if (is_post()) {
    $targetShopId = post_int('shop_id') ?: null;
    $design = array_key_exists((string) post('receipt_template'), $DESIGNS) ? (string) post('receipt_template') : 'classic';

    $exists = $targetShopId
        ? Database::first('SELECT id FROM themes WHERE shop_id = ?', [$targetShopId])
        : Database::first('SELECT id FROM themes WHERE shop_id IS NULL');

    if ($exists) {
        $sql = 'UPDATE themes SET receipt_template=?, version = version + 1 WHERE ' . ($targetShopId ? 'shop_id=?' : 'shop_id IS NULL');
        $params = $targetShopId ? [$design, $targetShopId] : [$design];
        Database::run($sql, $params);
    } else {
        Database::run('INSERT INTO themes (shop_id, receipt_template) VALUES (?,?)', [$targetShopId, $design]);
    }

    AdminLog::write('receipt_template_updated', 'theme', (string) ($targetShopId ?? 'global'), $targetShopId, ['template' => $design]);
    Flash::success('Receipt design published — devices apply it on the next config sync.');
    redirect(url('receipts', $targetShopId ? ['shop_id' => $targetShopId] : []));
}

$theme = $shopId
    ? Database::first('SELECT * FROM themes WHERE shop_id = ?', [$shopId])
    : Database::first('SELECT * FROM themes WHERE shop_id IS NULL');
$current = $theme['receipt_template'] ?? 'classic';
?>

<div class="d-flex flex-wrap align-items-center gap-2 mb-3">
  <form method="get" class="d-flex align-items-center gap-2">
    <input type="hidden" name="p" value="receipts">
    <label class="small text-secondary mb-0">Scope</label>
    <select name="shop_id" class="form-select form-select-sm" style="width:auto" onchange="this.form.submit()">
      <option value="0" <?= $shopId === 0 ? 'selected' : '' ?>>Global default (all shops)</option>
      <?php foreach ($shops as $s): ?>
        <option value="<?= (int) $s['id'] ?>" <?= $shopId === (int) $s['id'] ? 'selected' : '' ?>>Override: <?= e($s['name']) ?></option>
      <?php endforeach; ?>
    </select>
  </form>
  <span class="ms-auto small text-secondary">Current: <strong><?= e($current) ?></strong></span>
</div>

<div class="row g-3">
  <?php foreach ($DESIGNS as $key => $desc): ?>
    <div class="col-md-6 col-lg-4">
      <div class="card h-100 <?= $current === $key ? 'border-primary' : '' ?>">
        <div class="card-body d-flex flex-column">
          <h6 class="text-capitalize"><?= e($key) ?> <?= $current === $key ? '<span class="badge text-bg-primary">Active</span>' : '' ?></h6>
          <p class="small text-secondary flex-grow-1"><?= e($desc) ?></p>
          <form method="post">
            <?= Csrf::field() ?>
            <input type="hidden" name="shop_id" value="<?= (int) $shopId ?>">
            <input type="hidden" name="receipt_template" value="<?= e($key) ?>">
            <button class="btn btn-sm w-100 <?= $current === $key ? 'btn-primary' : 'btn-outline-primary' ?>" <?= $current === $key ? 'disabled' : '' ?>>
              <?= $current === $key ? 'Selected' : 'Select' ?>
            </button>
          </form>
        </div>
      </div>
    </div>
  <?php endforeach; ?>
</div>
