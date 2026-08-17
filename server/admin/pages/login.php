<?php
/** Sign-in screen for super admins. */

use Admin\AdminAuth;
use Admin\Csrf;
use Admin\Flash;
use Admin\Session;

if (AdminAuth::check()) {
    redirect(url('dashboard'));
}

$error = '';

if (is_post()) {
    $tries = (int) Session::get('login_tries', 0);
    $until = (int) Session::get('login_lock_until', 0);

    if ($until > time()) {
        $error = 'Too many attempts. Try again in ' . ($until - time()) . ' seconds.';
    } else {
        $username = post('username');
        $password = (string) ($_POST['password'] ?? '');

        if ($username === '' || $password === '') {
            $error = 'Enter both username and password.';
        } elseif (AdminAuth::attempt($username, $password)) {
            Session::forget('login_tries');
            Session::forget('login_lock_until');
            Flash::success('Welcome back, ' . (AdminAuth::user()['full_name'] ?? 'Admin') . '.');
            redirect(url('dashboard'));
        } else {
            $tries++;
            Session::set('login_tries', $tries);
            if ($tries >= 5) {
                Session::set('login_lock_until', time() + 300);
                Session::set('login_tries', 0);
                $error = 'Too many failed attempts. Locked for 5 minutes.';
            } else {
                $error = 'Invalid credentials.';
            }
            usleep(400000); // slow brute force
        }
    }
}
?>
<div class="auth-card">
  <div class="d-flex align-items-center gap-3 mb-4">
    <span class="brand-dot"></span>
    <div>
      <h1 class="h5 mb-0">QuickTap POS</h1>
      <div class="text-secondary small text-uppercase" style="letter-spacing:.14em">Super Admin</div>
    </div>
  </div>

  <?php if ($error !== ''): ?>
    <div class="alert alert-danger py-2"><?= e($error) ?></div>
  <?php endif; ?>

  <form method="post" autocomplete="off">
    <?= Csrf::field() ?>
    <div class="mb-3">
      <label class="form-label small">Username</label>
      <input name="username" class="form-control" required autofocus value="<?= e(post('username')) ?>">
    </div>
    <div class="mb-4">
      <label class="form-label small">Password</label>
      <input name="password" type="password" class="form-control" required>
    </div>
    <button class="btn btn-primary w-100 py-2 fw-semibold">Sign in</button>
  </form>

  <p class="text-secondary small mt-4 mb-0">
    Sessions lock automatically after 30 minutes of inactivity.
  </p>
</div>
