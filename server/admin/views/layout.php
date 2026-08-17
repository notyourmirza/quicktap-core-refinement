<?php
/** @var string $content @var string $pageTitle @var string $activePage */
use Admin\AdminAuth;
use Admin\Flash;

$admin  = AdminAuth::user();
$flash  = Flash::pull();
$isAuth = $admin !== null;
$nav = [
    ['dashboard',     'Dashboard',      'speedometer2'],
    ['shops',         'Shops',          'shop'],
    ['licenses',      'Licences',       'key'],
    ['credits',       'Credits',        'coin'],
    ['devices',       'Devices',        'phone'],
    ['reports',       'Reports',        'graph-up'],
    ['plans',         'Plans',          'credit-card'],
    ['market_requests','Purchase requests','receipt'],
    ['whatsapp',      'WhatsApp support','whatsapp'],
    ['splash',        'Splash screen',  'phone-vibrate'],
    ['branding',      'Branding',       'palette'],
    ['receipts',      'Receipt designs','receipt-cutoff'],
    ['versions',      'App versions',   'android2'],
    ['notifications', 'Notifications',  'bell'],
    ['backups',       'Backups',        'cloud-arrow-up'],
    ['logs',          'Activity logs',  'clock-history'],
    ['api_clients',   'API clients',    'key'],
    ['admins',        'Super admins',   'people'],
];
?>
<!doctype html>
<html lang="en" data-bs-theme="dark">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex, nofollow">
<title><?= e($pageTitle) ?> — QuickTap Super Admin</title>
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
<link href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css" rel="stylesheet">
<link href="assets/admin.css" rel="stylesheet">
</head>
<body class="<?= $isAuth ? 'app-shell' : 'auth-shell' ?>">

<?php if ($isAuth): ?>
<aside class="sidebar">
  <div class="brand">
    <span class="brand-dot"></span>
    <div>
      <strong>QuickTap</strong>
      <small>Super Admin</small>
    </div>
  </div>
  <nav class="nav flex-column">
    <?php foreach ($nav as [$slug, $label, $icon]): ?>
      <a class="nav-link<?= $activePage === $slug || ($activePage === 'shop' && $slug === 'shops') ? ' active' : '' ?>"
         href="<?= e(url($slug)) ?>">
        <i class="bi bi-<?= e($icon) ?>"></i><span><?= e($label) ?></span>
      </a>
    <?php endforeach; ?>
  </nav>
  <div class="sidebar-foot">
    <a class="nav-link<?= $activePage === 'profile' ? ' active' : '' ?>" href="<?= e(url('profile')) ?>">
      <i class="bi bi-person-circle"></i><span><?= e($admin['full_name'] ?? 'Profile') ?></span>
    </a>
    <a class="nav-link text-danger" href="<?= e(url('logout')) ?>"><i class="bi bi-box-arrow-right"></i><span>Sign out</span></a>
  </div>
</aside>

<main class="content">
  <header class="topbar">
    <button class="btn btn-sm btn-outline-secondary d-lg-none" id="sidebarToggle"><i class="bi bi-list"></i></button>
    <h1 class="h5 mb-0"><?= e($pageTitle) ?></h1>
    <span class="ms-auto small text-secondary"><?= date('D, d M Y H:i') ?> UTC</span>
  </header>

  <div class="page">
    <?php foreach ($flash as $f): ?>
      <div class="alert alert-<?= e($f['type']) ?> alert-dismissible fade show" role="alert">
        <?= e($f['message']) ?>
        <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
      </div>
    <?php endforeach; ?>
    <?= $content ?>
  </div>
</main>
<?php else: ?>
  <?php foreach ($flash as $f): ?>
    <div class="alert alert-<?= e($f['type']) ?> auth-flash"><?= e($f['message']) ?></div>
  <?php endforeach; ?>
  <?= $content ?>
<?php endif; ?>

<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js"></script>
<script src="assets/admin.js"></script>
</body>
</html>
