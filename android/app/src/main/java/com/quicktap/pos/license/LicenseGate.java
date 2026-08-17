package com.quicktap.pos.license;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.quicktap.pos.auth.SessionManager;
import com.quicktap.pos.ui.LoginActivity;
import com.quicktap.pos.ui.MainActivity;
import com.quicktap.pos.ui.license.ConfirmCredentialsActivity;
import com.quicktap.pos.ui.license.LicenseStatusActivity;
import com.quicktap.pos.util.AppPrefs;

/**
 * Decides which screen the user belongs on, based on the SERVER's answer.
 *
 * The cached state is only ever used to keep an already-verified shop selling
 * for a short grace window while the network is down — it can never unlock an
 * account that the server has not activated, and protected API calls stay
 * rejected server-side regardless of anything cached here.
 */
public final class LicenseGate {

    /** How long an already-confirmed ACTIVE licence survives without a server answer. */
    private static final long OFFLINE_GRACE_MS = 24 * 60 * 60 * 1000L;

    public interface Router { void onRoute(LicenseState state); }

    private LicenseGate() { }

    // ------------------------------------------------------------- routing

    /** Asks the server for the current state, then routes the activity. */
    public static void resolveAndRoute(Activity activity) {
        if (!SessionManager.get(activity).isSignedIn()) {
            open(activity, LoginActivity.class);
            return;
        }
        LicenseService.status(activity, state -> route(activity, state));
    }

    /** Routes on an already-fetched state. Safe to call from any screen. */
    public static void route(Activity activity, LicenseState state) {
        if (activity == null || activity.isFinishing()) return;

        if (LicenseState.OFFLINE.equals(state.state)) {
            if (offlineGraceAllows(activity)) {
                open(activity, MainActivity.class);
            } else {
                openStatus(activity, LicenseState.OFFLINE);
            }
            return;
        }

        switch (state.state) {
            case LicenseState.NO_ACCOUNT:
                SessionManager.get(activity).forceSignOut();
                AppPrefs.get(activity).clearLicenseCache();
                open(activity, LoginActivity.class);
                return;
            case LicenseState.ACTIVE:
                if (state.unlocked && state.confirmed) {
                    open(activity, MainActivity.class);
                } else if (state.unlocked) {
                    open(activity, ConfirmCredentialsActivity.class);
                } else {
                    openStatus(activity, LicenseState.PENDING);
                }
                return;
            default:
                openStatus(activity, state.state);
        }
    }

    /** True while an already-confirmed ACTIVE licence may ride out a network outage. */
    public static boolean offlineGraceAllows(Context ctx) {
        AppPrefs prefs = AppPrefs.get(ctx);
        if (!LicenseState.ACTIVE.equals(prefs.getLicenseState())) return false;
        if (!prefs.isLicenseConfirmed()) return false;

        long lastOk = prefs.getLastLicenseSuccessAt();
        if (lastOk <= 0) return false;
        long since = System.currentTimeMillis() - lastOk;
        if (since < 0 || since > OFFLINE_GRACE_MS) return false;

        // Expiry always comes from the SERVER clock recorded at the last check;
        // the grace window can never reach past it.
        long expiry = prefs.getLicenseExpiresAt();
        return expiry <= 0 || expiry > lastOk;
    }

    /** Cached hint used by the banner; never an authorisation decision. */
    public static boolean looksUnlocked(Context ctx) {
        AppPrefs prefs = AppPrefs.get(ctx);
        return LicenseState.ACTIVE.equals(prefs.getLicenseState()) && prefs.isLicenseConfirmed();
    }

    /** True when a background refresh is due (interval published by the server). */
    public static boolean refreshDue(Context ctx) {
        AppPrefs prefs = AppPrefs.get(ctx);
        long interval = Math.max(5, prefs.getLicenseSyncMinutes()) * 60_000L;
        return System.currentTimeMillis() - prefs.getLastLicenseCheck() > interval;
    }

    // ------------------------------------------------------------- helpers

    private static void openStatus(Activity activity, String state) {
        Intent intent = new Intent(activity, LicenseStatusActivity.class)
                .putExtra(LicenseStatusActivity.EXTRA_STATE, state)
                // A locked-out account must not be able to press Back into the app.
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }

    private static void open(Activity activity, Class<?> target) {
        if (activity.getClass().equals(target)) return;
        activity.startActivity(new Intent(activity, target)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        activity.finish();
    }
}
