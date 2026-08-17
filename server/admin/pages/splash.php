<?php
/** Splash screen full control — global row (shop_id NULL) plus optional per-shop override. */

use Admin\AdminLog;
use Admin\Csrf;
use Admin\Flash;
use QuickTap\Core\Database;

$ANIMATIONS = [
    'fade'     => 'Fade in',
    'zoom'     => 'Zoom in',
    'slide_up' => 'Slide up',
    'pulse'    => 'Pulse',
    'rotate'   => 'Rotate',
];

$shopId = query_int('shop_id'); // 0 = global
$shops  = Database::all('SELECT id, name FROM shops WHERE deleted_at IS NULL ORDER BY name');

if (is_post()) {
    $targetShopId = post_int('shop_id') ?: null;
    $enabled       = post_bool('enabled');
    $title         = post('title', 'QuickTap POS') ?: 'QuickTap POS';
    $tagline       = post('tagline', 'Fast. Simple. Reliable.') ?: 'Fast. Simple. Reliable.';
    $creditText    = post('credit_text', 'MA Technologies') ?: 'MA Technologies';
    $creditPrefix  = post('credit_prefix', 'Powered by') ?: 'Powered by';
    $logoUrl       = post_null('logo_url');
    $background    = valid_hex(post('background_color'), '#0B0F19');
    $textColor     = valid_hex(post('text_color'), '#FFFFFF');
    $accentColor   = valid_hex(post('accent_color'), '#0E9F6E');
    $animation     = pick(post('animation'), array_keys($ANIMATIONS), 'fade');
    $duration      = max(300, min(10000, post_int('duration_ms', 1800)));
    $showCredit    = post_bool('show_credit');
    $showProgress  = post_bool('show_progress');

    $exists = $targetShopId
        ? Database::first('SELECT id FROM splash_config WHERE shop_id = ?', [$targetShopId])
        : Database::first('SELECT id FROM splash_config WHERE shop_id IS NULL');

    $params = [
        $enabled, $title, $tagline, $creditText, $creditPrefix, $logoUrl,
        $background, $textColor, $accentColor, $animation, $duration, $showCredit, $showProgress,
    ];
    if ($exists) {
        $sql = 'UPDATE splash_config SET enabled=?, title=?, tagline=?, credit_text=?, credit_prefix=?, logo_url=?,
                    background_color=?, text_color=?, accent_color=?, animation=?, duration_ms=?, show_credit=?,
                    show_progress=?, version = version + 1
              WHERE ' . ($targetShopId ? 'shop_id = ?' : 'shop_id IS NULL');
        if ($targetShopId) {
            $params[] = $targetShopId;
        }
        Database::run($sql, $params);
    } else {
        Database::run(
            'INSERT INTO splash_config (shop_id, enabled, title, tagline, credit_text, credit_prefix, logo_url,
                    background_color, text_color, accent_color, animation, duration_ms, show_credit, show_progress)
             VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)',
            [
                $targetShopId, $enabled, $title, $tagline, $creditText, $creditPrefix, $logoUrl,
                $background, $textColor, $accentColor, $animation, $duration, $showCredit, $showProgress,
            ]
        );
    }

    AdminLog::write('splash_updated', 'splash_config', (string) ($targetShopId ?? 'global'), $targetShopId,
        ['title' => $title, 'animation' => $animation]);
    Flash::success('Splash screen saved — devices apply it on the next config sync.');
    redirect(url('splash', $targetShopId ? ['shop_id' => $targetShopId] : []));
}

$row = $shopId
    ? Database::first('SELECT * FROM splash_config WHERE shop_id = ?', [$shopId])
    : Database::first('SELECT * FROM splash_config WHERE shop_id IS NULL');

$defaults = [
    'enabled' => 1, 'title' => 'QuickTap POS', 'tagline' => 'Fast. Simple. Reliable.',
    'credit_text' => 'MA Technologies', 'credit_prefix' => 'Powered by', 'logo_url' => '',
    'background_color' => '#0B0F19', 'text_color' => '#FFFFFF', 'accent_color' => '#0E9F6E',
    'animation' => 'fade', 'duration_ms' => 1800, 'show_credit' => 1, 'show_progress' => 1, 'version' => 0,
];
$splash = $row ?: $defaults;
?>

<div class="d-flex flex-wrap align-items-center gap-2 mb-3">
  <form method="get" class="d-flex align-items-center gap-2">
    <input type="hidden" name="p" value="splash">
    <label class="small text-secondary mb-0">Scope</label>
    <select name="shop_id" class="form-select form-select-sm" style="width:auto" onchange="this.form.submit()">
      <option value="0" <?= $shopId === 0 ? 'selected' : '' ?>>Global default (all shops)</option>
      <?php foreach ($shops as $s): ?>
        <option value="<?= (int) $s['id'] ?>" <?= $shopId === (int) $s['id'] ? 'selected' : '' ?>>Override: <?= e($s['name']) ?></option>
      <?php endforeach; ?>
    </select>
  </form>
</div>

<div class="row g-3">
  <div class="col-lg-7">
    <form method="post" class="card">
      <?= Csrf::field() ?>
      <input type="hidden" name="shop_id" value="<?= (int) $shopId ?>">
      <div class="card-header">Splash screen (version <?= (int) $splash['version'] ?>)</div>
      <div class="card-body row g-3">
        <div class="col-12 form-check form-switch ms-1">
          <input class="form-check-input" type="checkbox" role="switch" name="enabled" value="1" id="spEnabled" <?= (int) $splash['enabled'] ? 'checked' : '' ?>>
          <label class="form-check-label" for="spEnabled">Show splash screen on launch</label>
        </div>
        <div class="col-md-6"><label class="form-label small">Title</label><input name="title" id="spTitle" class="form-control" value="<?= e($splash['title']) ?>" oninput="spPreview()"></div>
        <div class="col-md-6"><label class="form-label small">Tagline</label><input name="tagline" id="spTagline" class="form-control" value="<?= e($splash['tagline']) ?>" oninput="spPreview()"></div>
        <div class="col-md-6"><label class="form-label small">Credit prefix</label><input name="credit_prefix" id="spCreditPrefix" class="form-control" value="<?= e($splash['credit_prefix']) ?>" oninput="spPreview()"></div>
        <div class="col-md-6"><label class="form-label small">Credit text</label><input name="credit_text" id="spCreditText" class="form-control" value="<?= e($splash['credit_text']) ?>" oninput="spPreview()"></div>
        <div class="col-12"><label class="form-label small">Logo URL</label><input name="logo_url" id="spLogo" class="form-control" value="<?= e($splash['logo_url']) ?>" oninput="spPreview()"></div>
        <div class="col-md-4">
          <label class="form-label small">Background</label>
          <input type="color" name="background_color" id="spBg" class="form-control form-control-color" value="<?= e($splash['background_color']) ?>" oninput="spPreview()">
        </div>
        <div class="col-md-4">
          <label class="form-label small">Text colour</label>
          <input type="color" name="text_color" id="spText" class="form-control form-control-color" value="<?= e($splash['text_color']) ?>" oninput="spPreview()">
        </div>
        <div class="col-md-4">
          <label class="form-label small">Accent colour</label>
          <input type="color" name="accent_color" id="spAccent" class="form-control form-control-color" value="<?= e($splash['accent_color']) ?>" oninput="spPreview()">
        </div>
        <div class="col-md-6">
          <label class="form-label small">Animation</label>
          <select name="animation" id="spAnim" class="form-select">
            <?php foreach ($ANIMATIONS as $k => $label): ?>
              <option value="<?= e($k) ?>" <?= $splash['animation'] === $k ? 'selected' : '' ?>><?= e($label) ?></option>
            <?php endforeach; ?>
          </select>
        </div>
        <div class="col-md-6"><label class="form-label small">Duration (ms)</label><input type="number" name="duration_ms" id="spDuration" min="300" max="10000" step="100" class="form-control" value="<?= (int) $splash['duration_ms'] ?>"></div>
        <div class="col-md-6 form-check form-switch ms-1">
          <input class="form-check-input" type="checkbox" role="switch" name="show_credit" value="1" id="spShowCredit" <?= (int) $splash['show_credit'] ? 'checked' : '' ?> onchange="spPreview()">
          <label class="form-check-label" for="spShowCredit">Show "Powered by" credit</label>
        </div>
        <div class="col-md-6 form-check form-switch ms-1">
          <input class="form-check-input" type="checkbox" role="switch" name="show_progress" value="1" id="spShowProgress" <?= (int) $splash['show_progress'] ? 'checked' : '' ?>>
          <label class="form-check-label" for="spShowProgress">Show loading progress indicator</label>
        </div>
      </div>
      <div class="card-footer bg-transparent border-0 d-flex justify-content-between align-items-center pb-3 px-3">
        <span class="small text-secondary">Saving bumps the splash version so devices refresh on next sync.</span>
        <button class="btn btn-primary">Save splash screen</button>
      </div>
    </form>
  </div>
  <div class="col-lg-5">
    <div class="card h-100">
      <div class="card-header">Live preview</div>
      <div class="card-body">
        <div id="spPreviewBox" class="rounded-4 p-4 text-center d-flex flex-column justify-content-center align-items-center"
             style="min-height:320px;background:<?= e($splash['background_color']) ?>;color:<?= e($splash['text_color']) ?>">
          <?php if ($splash['logo_url']): ?>
            <img src="<?= e($splash['logo_url']) ?>" id="spPreviewLogo" alt="logo" style="max-width:96px;max-height:96px" class="mb-3">
          <?php else: ?>
            <div id="spPreviewLogo" class="mb-3 rounded-circle d-flex align-items-center justify-content-center"
                 style="width:72px;height:72px;background:<?= e($splash['accent_color']) ?>;color:#fff;font-weight:700;font-size:1.5rem;">Q</div>
          <?php endif; ?>
          <h4 id="spPreviewTitle" class="fw-bold mb-1"><?= e($splash['title']) ?></h4>
          <p id="spPreviewTagline" class="mb-4 opacity-75"><?= e($splash['tagline']) ?></p>
          <?php if ((int) $splash['show_progress']): ?>
            <div class="spinner-border spinner-border-sm mb-4" style="color:<?= e($splash['accent_color']) ?>" role="status"></div>
          <?php endif; ?>
          <?php if ((int) $splash['show_credit']): ?>
            <small id="spPreviewCredit" class="opacity-50"><?= e($splash['credit_prefix']) ?> <?= e($splash['credit_text']) ?></small>
          <?php endif; ?>
        </div>
      </div>
    </div>
  </div>
</div>

<script>
function spPreview() {
  const box = document.getElementById('spPreviewBox');
  box.style.background = document.getElementById('spBg').value;
  box.style.color = document.getElementById('spText').value;
  document.getElementById('spPreviewTitle').textContent = document.getElementById('spTitle').value;
  document.getElementById('spPreviewTagline').textContent = document.getElementById('spTagline').value;
  const credit = document.getElementById('spPreviewCredit');
  if (credit) {
    credit.textContent = document.getElementById('spCreditPrefix').value + ' ' + document.getElementById('spCreditText').value;
  }
}
</script>
