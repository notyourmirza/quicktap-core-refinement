package com.quicktap.pos.license;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import com.quicktap.pos.util.AppExecutors;
import com.quicktap.pos.util.AppPrefs;

import java.util.UUID;

/**
 * Owns device registration and the periodic licence re-check.
 *
 * Offline-first rule: the app keeps working from the cached status. A blocked
 * device or an expired subscription locks the app on the next successful check,
 * and an expired local date locks it immediately even with no network.
 */
public class LicenseManager {

    /** Re-check the licence at most once every 6 hours. */
    private static final long CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L;

    public interface Listener {
        void onResult(boolean allowed, String status, String message);
    }

    private final Context context;
    private final AppPrefs prefs;

    public LicenseManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = AppPrefs.get(this.context);
    }

    @SuppressLint("HardwareIds")
    public String deviceId() {
        String cached = prefs.getDeviceId();
        if (cached != null) return cached;
        String androidId = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ANDROID_ID);
        String id = androidId == null || androidId.isEmpty()
                ? UUID.randomUUID().toString()
                : androidId;
        prefs.setDeviceId(id);
        return id;
    }

    public String deviceName() { return Build.MANUFACTURER + " " + Build.MODEL; }

    public boolean isSignedIn() { return prefs.getAuthToken() != null; }

    /** True when the cached licence still allows the app to open. */
    public boolean isCurrentlyAllowed() {
        if (!"APPROVED".equals(prefs.getLicenseStatus())) return false;
        long expiry = prefs.getLicenseExpiry();
        return expiry <= 0 || expiry > System.currentTimeMillis();
    }

    public void login(String email, String password, Listener listener) {
        AppExecutors.io().execute(() -> {
            LicenseResult result = LicenseApi.login(
                    email, password, deviceId(), deviceName(), "1.0");
            if (result.networkOk && result.success && result.token != null) {
                prefs.setUserEmail(email);
                prefs.setAuthToken(result.token);
                // Re-installed device: the server may already know this device_id
                // and have it APPROVED. Always ask for the authoritative status
                // instead of assuming a fresh "pending approval" enrolment.
                LicenseResult status = LicenseApi.check(deviceId(), prefs.getAuthToken());
                if (status.networkOk) result = status;
            }
            applyAndNotify(result, listener, "Cannot reach the licence server. Check your internet.");
        });
    }

    /**
     * Authoritative status check used before any lock screen is shown, so an
     * already-approved device never sees "waiting for approval" after a
     * re-install. Falls back to the cached status when offline.
     */
    public void verifyStatus(Listener listener) {
        AppExecutors.io().execute(() -> {
            if (!isSignedIn()) {
                notifyCached(listener);
                return;
            }
            LicenseResult result = LicenseApi.check(deviceId(), prefs.getAuthToken());
            if (!result.networkOk) {
                notifyCached(listener);
                return;
            }
            applyAndNotify(result, listener, result.message);
        });
    }

    // ---- pending-approval polling ----

    private static final long POLL_INTERVAL_MS = 10_000L;
    private final android.os.Handler poller =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pollTask;

    /** Repeatedly re-checks the licence while the waiting screen is visible. */
    public void startPolling(Listener listener) {
        stopPolling();
        pollTask = new Runnable() {
            @Override public void run() {
                verifyStatus((allowed, status, message) -> {
                    listener.onResult(allowed, status, message);
                    if (!allowed && pollTask != null) poller.postDelayed(pollTask, POLL_INTERVAL_MS);
                });
            }
        };
        poller.post(pollTask);
    }

    public void stopPolling() {
        if (pollTask != null) poller.removeCallbacks(pollTask);
        pollTask = null;
    }

    /** Silent background re-check; skips when the last check is still fresh. */
    public void refreshIfDue(Listener listener) {
        long since = System.currentTimeMillis() - prefs.getLastLicenseCheck();
        if (since < CHECK_INTERVAL_MS) {
            notifyCached(listener);
            return;
        }
        forceRefresh(listener);
    }

    public void forceRefresh(Listener listener) {
        AppExecutors.io().execute(() -> {
            LicenseResult result = LicenseApi.check(deviceId(), prefs.getAuthToken());
            if (!result.networkOk) {
                // Offline: keep whatever the cache says.
                notifyCached(listener);
                return;
            }
            prefs.setLastLicenseCheck(System.currentTimeMillis());
            applyAndNotify(result, listener, result.message);
        });
    }

    public void activateWithCode(String code, Listener listener) {
        AppExecutors.io().execute(() -> {
            LicenseResult result = LicenseApi.activate(deviceId(), code);
            applyAndNotify(result, listener, "Cannot reach the licence server. Check your internet.");
        });
    }

    private void applyAndNotify(LicenseResult result, Listener listener, String offlineMessage) {
        if (!result.networkOk) {
            AppExecutors.main().post(() ->
                    listener.onResult(isCurrentlyAllowed(), prefs.getLicenseStatus(), offlineMessage));
            return;
        }
        prefs.setLicenseStatus(result.status);
        prefs.setLicenseMessage(result.message);
        if (result.expiresAt > 0) prefs.setLicenseExpiry(result.expiresAt);
        prefs.setLastLicenseCheck(System.currentTimeMillis());

        boolean allowed = isCurrentlyAllowed();
        String message = result.message.isEmpty() ? describe(result.status) : result.message;
        AppExecutors.main().post(() -> listener.onResult(allowed, result.status, message));
    }

    private void notifyCached(Listener listener) {
        AppExecutors.main().post(() -> listener.onResult(
                isCurrentlyAllowed(), prefs.getLicenseStatus(), describe(prefs.getLicenseStatus())));
    }

    private String describe(String status) {
        switch (status) {
            case "APPROVED": return "Licence active";
            case "REJECTED": return "This device was rejected by the administrator.";
            case "BLOCKED": return "This device has been blocked. Contact your administrator.";
            case "EXPIRED": return "Your subscription has expired. Please renew to continue.";
            default: return "Waiting for administrator approval of this device.";
        }
    }

    public void signOut() {
        prefs.signOut();
    }
}
