<?php
/** Branding control — app name, theme preset, brand colours, logo and splash image.
 *  Scope: global default (themes.shop_id NULL) or a per-shop override. */

use Admin\AdminLog;
use Admin\Csrf;
use Admin\Flash;
use QuickTap\Core\Database;

/** Same ten design languages the Android app ships (ThemePresets.java). */
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

$shopId = query_int('shop_id'); // 0 = global default
$shops  = Database::all('SELECT id, name FROM shops WHERE deleted_at IS NULL ORDER BY name');

if (is_post()) {
    $targetShopId = post_int('shop_id') ?: null;
    $themeKey = array_key_exists((string) post('theme_key'), $THEMES)
        ? (string) post('theme_key') : 'material_you';
    $appName   = trim((string) post('app_name', 'QuickTap POS'));
    $appName   = $appName === '' ? 'QuickTap POS' : mb_substr($appName, 0, 40);
    $primary   = valid_hex(post('primary_color'), $THEMES[$themeKey][1]);
    $secondary = valid_hex(post('secondary_color'), $THEMES[$themeKey][2]);
    $logoUrl   = post_null('logo_url');
    $splashUrl = post_null('splash_url');

    $exists = $targetShopId
        ? Database::first('SELECT id FROM themes WHERE shop_id = ?', [$targetShopId])
        : Database::first('SELECT id FROM themes WHERE shop_id IS NULL');

    if ($exists) {
        $sql = 'UPDATE themes SET theme_key=?, app_name=?, primary_color=?, secondary_color=?,
                       logo_url=?, splash_url=?, version = version + 1
                 WHERE ' . ($targetShopId ? 'shop_id=?' : 'shop_id IS NULL');
        $params = [$themeKey, $appName, $primary, $secondary, $logoUrl, $splashUrl];
        if ($targetShopId) { $params[] = $targetShopId; }
        Database::run($sql, $params);
    } else {
        Database::run(
            'INSERT INTO themes (shop_id, theme_key, app_name, primary_color, secondary_color, logo_url, splash_url)
             VALUES (?,?,?,?,?,?,?)',
            [$targetShopId, $themeKey, $appName, $primary, $secondary, $logoUrl, $splashUrl]
        );
    }

    AdminLog::write('branding_updated', 'theme', (string) ($targetShopId ?? 'global'), $targetShopId, [
        'app_name' => $appName, 'theme' => $themeKey, 'primary' => $primary,
    ]);
    Flash::success('Branding published — devices pick it up on the next config sync.');
    redirect(url('branding', $targetShopId ? ['shop_id' => $targetShopId] : []));
}

$theme = ($shopId
    ? Database::first('SELECT * FROM themes WHERE shop_id = ?', [$shopId])
    : Database::first('SELECT * FROM themes WHERE shop_id IS NULL')) ?: [
        'theme_key' => 'material_you', 'app_name' => 'QuickTap POS',
        'primary_color' => '#0E9F6E', 'secondary_color' => '#34D399',
        'logo_url' => '', 'splash_url' => '', 'version' => 0,
    ];
?>

<div class="d-flex flex-wrap align-items-center gap-2 mb-3">
  <form method="get" class="d-flex align-items-center gap-2">
    <input type="hidden" name="p" value="branding">
    <label class="small text-secondary mb-0">Scope</label>
    <select name="shop_id" class="form-select form-select-sm" style="width:auto" onchange="this.form.submit()">
      <option value="0" <?= $shopId === 0 ? 'selected' : '' ?>>Global default (all shops)</option>
      <?php foreach ($shops as $s): ?>
        <option value="<?= (int) $s['id'] ?>" <?= $shopId === (int) $s['id'] ? 'selected' : '' ?>>Override: <?= e($s['name']) ?></option>
      <?php endforeach; ?>
    </select>
  </form>
  <span class="ms-auto small text-secondary">Theme version <strong><?= (int) ($theme['version'] ?? 0) ?></strong></span>
</div>

<div class="row g-3">
  <div class="col-lg-8">
    <form method="post" class="card">
      <?= Csrf::field() ?>
      <input type="hidden" name="shop_id" value="<?= (int) $shopId ?>">
      <div class="card-header">App identity</div>
      <div class="card-body row g-3">
        <div class="col-12">
          <label class="form-label small">App name shown on splash, toolbar and receipts</label>
          <input name="app_name" maxlength="40" class="form-control" value="<?= e($theme['app_name'] ?? 'QuickTap POS') ?>" required>
        </div>
        <div class="col-md-6">
          <label class="form-label small">Premium theme preset</label>
          <select name="theme_key" class="form-select">
            <?php foreach ($THEMES as $tk => $tv): ?>
              <option value="<?= e($tk) ?>" <?= (($theme['theme_key'] ?? 'material_you') === $tk) ? 'selected' : '' ?>><?= e($tv[0]) ?></option>
            <?php endforeach; ?>
          </select>
        </div>
        <div class="col-md-3">
          <label class="form-label small">Primary</label>
          <input type="color" name="primary_color" class="form-control form-control-color" data-preview="#brandPrimary" value="<?= e($theme['primary_color']) ?>">
        </div>
        <div class="col-md-3">
          <label class="form-label small">Secondary</label>
          <input type="color" name="secondary_color" class="form-control form-control-color" data-preview="#brandSecondary" value="<?= e($theme['secondary_color']) ?>">
        </div>
        <div class="col-md-6">
          <label class="form-label small">Logo URL</label>
          <input name="logo_url" class="form-control" value="<?= e($theme['logo_url'] ?? '') ?>">
        </div>
        <div class="col-md-6">
          <label class="form-label small">Splash image URL</label>
          <input name="splash_url" class="form-control" value="<?= e($theme['splash_url'] ?? '') ?>">
        </div>
      </div>
      <div class="card-footer d-flex align-items-center gap-2">
        <button class="btn btn-primary btn-sm">Publish branding</button>
        <span class="small text-secondary">Publishing bumps the theme version so every bound device refreshes.</span>
      </div>
    </form>
  </div>

  <div class="col-lg-4">
    <div class="card h-100">
      <div class="card-header">Preview</div>
      <div class="card-body">
        <div id="brandPrimary" class="rounded-3 mb-2" style="height:64px;background:<?= e($theme['primary_color']) ?>"></div>
        <div id="brandSecondary" class="rounded-3 mb-3" style="height:32px;background:<?= e($theme['secondary_color']) ?>"></div>
        <?php if (!empty($theme['logo_url'])): ?>
          <img src="<?= e($theme['logo_url']) ?>" alt="Brand logo" class="img-fluid rounded-3 mb-2">
        <?php endif; ?>
        <div class="small text-secondary">Receipt designs are managed on the <a href="<?= e(url('receipts')) ?>">Receipt designs</a> page, splash animation on the <a href="<?= e(url('splash')) ?>">Splash</a> page.</div>
      </div>
    </div>
  </div>
</div>
