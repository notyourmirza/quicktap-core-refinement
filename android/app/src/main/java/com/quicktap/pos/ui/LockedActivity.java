package com.quicktap.pos.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.quicktap.pos.databinding.ActivityLockedBinding;
import com.quicktap.pos.license.LicenseManager;
import com.quicktap.pos.util.AppPrefs;

/**
 * Full-screen approval / lock screen. While the device is PENDING it shows a
 * live "waiting for approval" state that keeps re-checking the licence server
 * automatically, so the cashier never has to guess when access is granted.
 */
public class LockedActivity extends AppCompatActivity {

    private ActivityLockedBinding binding;
    private LicenseManager license;
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private long waitingSince;
    private Runnable tick;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLockedBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        bindBrand();
        license = new LicenseManager(this);
        waitingSince = SystemClock.elapsedRealtime();

        AppPrefs prefs = AppPrefs.get(this);
        binding.textDeviceId.setText(license.deviceId());
        binding.textAccount.setText(prefs.getUserEmail() == null ? "—" : prefs.getUserEmail());
        binding.textDeviceName.setText(license.deviceName());
        render(prefs.getLicenseStatus(), prefs.getLicenseMessage());

        binding.buttonRetry.setOnClickListener(v -> {
            setBusy(true);
            license.verifyStatus(this::handle);
        });
        binding.buttonActivate.setOnClickListener(v -> activate());
        binding.buttonCopyId.setOnClickListener(v -> copyId());
        binding.buttonSignOut.setOnClickListener(v -> {
            license.stopPolling();
            license.signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        startPulse();
        startTicker();
    }

    @Override
    protected void onStart() {
        super.onStart();
        license.startPolling(this::handle);
    }

    @Override
    protected void onStop() {
        super.onStop();
        license.stopPolling();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ticker.removeCallbacksAndMessages(null);
    }

    private void activate() {
        String code = binding.inputCode.getText() == null
                ? "" : binding.inputCode.getText().toString().trim();
        if (code.length() < 4) {
            binding.layoutCode.setError("Enter the activation code");
            return;
        }
        binding.layoutCode.setError(null);
        setBusy(true);
        license.activateWithCode(code, this::handle);
    }

    private void copyId() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Device ID", license.deviceId()));
        Toast.makeText(this, "Device ID copied", Toast.LENGTH_SHORT).show();
    }

    /** Brand label follows the server driven app name. */
    private void bindBrand() {
        binding.textBrand.setText(
                com.quicktap.pos.util.AppPrefs.get(this).getThemeAppName());
    }

    private void handle(boolean allowed, String status, String message) {
        setBusy(false);
        if (allowed) {
            license.stopPolling();
            binding.textTitle.setText("Approved");
            binding.textMessage.setText("Your device is activated. Opening " + com.quicktap.pos.util.AppPrefs.get(this).getThemeAppName() + "…");
            Toast.makeText(this, "Licence active", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        render(status, message);
    }

    /** Paints the screen for the given licence status. */
    private void render(String status, String message) {
        boolean pending = status == null || status.isEmpty() || "PENDING".equals(status);
        binding.textStatus.setText(pending ? "AWAITING APPROVAL" : status);
        binding.textTitle.setText(pending ? "Waiting for approval" : title(status));
        binding.textMessage.setText(message == null || message.isEmpty()
                ? defaultMessage(status) : message);

        binding.groupWaiting.setVisibility(pending ? View.VISIBLE : View.GONE);
        binding.textStatus.setBackgroundResource(pending
                ? com.quicktap.pos.R.drawable.bg_pill_warning
                : com.quicktap.pos.R.drawable.bg_pill_danger);
    }

    private String title(String status) {
        switch (status) {
            case "REJECTED": return "Access rejected";
            case "BLOCKED":  return "Device blocked";
            case "EXPIRED":  return "Subscription expired";
            default:         return "Device locked";
        }
    }

    private String defaultMessage(String status) {
        switch (status) {
            case "REJECTED": return "The administrator rejected this device. Contact support to review the request.";
            case "BLOCKED":  return "This device has been blocked. Please contact your administrator.";
            case "EXPIRED":  return "Your subscription has ended. Renew it to continue billing.";
            default:         return "Your request has been sent to the administrator. This screen updates automatically the moment your device is approved.";
        }
    }

    private void startPulse() {
        binding.statusHalo.animate()
                .scaleX(1.06f).scaleY(1.06f).alpha(0.85f)
                .setDuration(1100)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> binding.statusHalo.animate()
                        .scaleX(1f).scaleY(1f).alpha(1f)
                        .setDuration(1100)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .withEndAction(this::startPulse)
                        .start())
                .start();
    }

    private void startTicker() {
        tick = new Runnable() {
            @Override public void run() {
                long seconds = (SystemClock.elapsedRealtime() - waitingSince) / 1000L;
                binding.textElapsed.setText(String.format(
                        java.util.Locale.getDefault(), "Waiting %02d:%02d", seconds / 60, seconds % 60));
                ticker.postDelayed(this, 1000L);
            }
        };
        ticker.post(tick);
    }

    private void setBusy(boolean busy) {
        binding.progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        binding.buttonRetry.setEnabled(!busy);
        binding.buttonActivate.setEnabled(!busy);
    }
}
