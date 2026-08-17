package com.quicktap.pos.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.quicktap.pos.auth.BiometricGate;
import com.quicktap.pos.auth.SessionManager;
import com.quicktap.pos.databinding.ActivityUnlockBinding;
import com.quicktap.pos.util.AppPrefs;

/**
 * Auto session lock screen.
 *
 * Shown when the app has been idle longer than the configured timeout. The
 * cashier unlocks with a fingerprint (when enabled) or with their password,
 * which is verified server-side against /v1/auth/unlock.
 */
public class UnlockActivity extends FragmentActivity {

    private ActivityUnlockBinding binding;
    private SessionManager session;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUnlockBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        session = SessionManager.get(this);
        session.lockNow();

        AppPrefs prefs = AppPrefs.get(this);
        String who = prefs.getFullName();
        if (TextUtils.isEmpty(who)) who = session.username();
        binding.textUser.setText(TextUtils.isEmpty(who) ? "Signed in" : "Signed in as " + who);

        boolean biometrics = session.isFingerprintEnabled() && BiometricGate.isAvailable(this);
        binding.buttonFingerprint.setVisibility(biometrics ? View.VISIBLE : View.GONE);

        binding.buttonUnlock.setOnClickListener(v -> unlockWithPassword());
        binding.buttonFingerprint.setOnClickListener(v -> promptBiometric());
        binding.buttonSignOut.setOnClickListener(v -> signOut());

        if (biometrics) promptBiometric();
    }

    private void promptBiometric() {
        BiometricGate.prompt(this, "Unlock " + com.quicktap.pos.util.AppPrefs.get(this).getThemeAppName(), "Confirm it's you to continue",
                new BiometricGate.Listener() {
                    @Override public void onSuccess() { unlocked(); }

                    @Override public void onFailure(String message, boolean fatal) {
                        if (fatal) showStatus(message + " — use your password instead");
                    }
                });
    }

    private void unlockWithPassword() {
        String password = binding.inputPassword.getText() == null
                ? "" : binding.inputPassword.getText().toString();
        if (password.length() < 4) {
            binding.layoutPassword.setError("Enter your password");
            return;
        }
        binding.layoutPassword.setError(null);
        setBusy(true);
        session.unlockWithPassword(password, (ok, message, code) -> {
            setBusy(false);
            if (ok) unlocked();
            else showStatus(message);
        });
    }

    private void unlocked() {
        session.markUnlocked();
        finish();
    }

    private void signOut() {
        setBusy(true);
        session.logout((ok, message, code) -> {
            startActivity(new Intent(this, LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
        });
    }

    private void showStatus(String message) {
        binding.textStatus.setText(message);
        binding.textStatus.setVisibility(View.VISIBLE);
    }

    private void setBusy(boolean busy) {
        binding.progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        binding.buttonUnlock.setEnabled(!busy);
    }

    /** The session stays locked — never let Back skip this screen. */
    @Override public void onBackPressed() { moveTaskToBack(true); }
}
