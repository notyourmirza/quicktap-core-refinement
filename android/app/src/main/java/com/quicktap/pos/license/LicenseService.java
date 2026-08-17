package com.quicktap.pos.license;

import android.content.Context;

import com.quicktap.pos.BuildConfig;
import com.quicktap.pos.auth.DeviceIdentity;
import com.quicktap.pos.auth.SessionManager;
import com.quicktap.pos.net.ApiClient;
import com.quicktap.pos.net.ApiResponse;
import com.quicktap.pos.util.AppExecutors;
import com.quicktap.pos.util.AppPrefs;

import org.json.JSONObject;

/**
 * The only Android entry point for the server-authoritative licence API.
 *
 * Every call goes through the shared {@link ApiClient}, which already resolves
 * its base URL from {@link com.quicktap.pos.net.RemoteEndpoint} (Firebase
 * controlled) and attaches the app credentials + JWT. No licence decision is
 * made here: the client only relays what the server said.
 *
 * Endpoints used:
 *   POST v1/auth/register     — create account + device registration
 *   GET  v1/license/status    — authoritative current state
 *   POST v1/license/verify    — redeem the licence key issued by the admin
 *   POST v1/license/confirm   — username + password confirmation
 *   GET  v1/app/config        — support number + server clock (pre-session)
 */
public final class LicenseService {

    public interface Callback { void onResult(LicenseState state); }

    private LicenseService() { }

    // ----------------------------------------------------------- register

    /**
     * Creates the account on the server. The device identifier comes from the
     * existing {@link DeviceIdentity} (hashed installation id) so the server can
     * enforce ONE DEVICE = ONE NEW ACCOUNT.
     */
    public static void register(Context ctx, String shopName, String username, String password,
                                String fullName, String phone, Callback cb) {
        final Context app = ctx.getApplicationContext();
        AppExecutors.io().execute(() -> {
            ApiResponse res;
            try {
                JSONObject body = new JSONObject()
                        .put("shop_name", shopName)
                        .put("username", username)
                        .put("password", password)
                        .put("full_name", fullName)
                        .put("phone", phone)
                        .put("device_id", DeviceIdentity.id(app))
                        .put("device_name", DeviceIdentity.name())
                        .put("app_version", BuildConfig.VERSION_NAME)
                        .put("os_version", DeviceIdentity.osVersion());
                res = ApiClient.post(app, "v1/auth/register", body, false);
            } catch (Exception e) {
                res = ApiResponse.offline(null);
            }
            if (res.success) {
                // The server already returned a session for the new account so
                // the pending screen can poll /v1/license/status straight away.
                SessionManager.get(app).adoptSession(res.dataOrEmpty(), username);
            }
            LicenseState state = LicenseState.from(res);
            cache(app, state);
            main(cb, state);
        });
    }

    // ------------------------------------------------------------- status

    /** Authoritative state for the signed-in account. Safe to call on demand. */
    public static void status(Context ctx, Callback cb) {
        final Context app = ctx.getApplicationContext();
        AppExecutors.io().execute(() -> {
            LicenseState state = statusBlocking(app);
            main(cb, state);
        });
    }

    /** Background-thread variant. */
    public static LicenseState statusBlocking(Context app) {
        if (!SessionManager.get(app).isSignedIn()) {
            LicenseState s = new LicenseState();
            s.state = LicenseState.NO_ACCOUNT;
            s.networkOk = true;
            s.message = "Create an account to continue.";
            return s;
        }
        ApiResponse res = ApiClient.get(app, "v1/license/status", null, true);
        LicenseState state = LicenseState.from(res);
        cache(app, state);
        return state;
    }

    // ------------------------------------------------------------- verify

    /** Redeems the licence key issued by the Super Admin. */
    public static void verify(Context ctx, String licenseKey, Callback cb) {
        final Context app = ctx.getApplicationContext();
        AppExecutors.io().execute(() -> {
            ApiResponse res;
            try {
                res = ApiClient.post(app, "v1/license/verify",
                        new JSONObject().put("license_key", licenseKey), true);
            } catch (Exception e) {
                res = ApiResponse.offline(null);
            }
            LicenseState state = LicenseState.from(res);
            cache(app, state);
            main(cb, state);
        });
    }

    // ------------------------------------------------------------ confirm

    /**
     * Username + password confirmation. The password is passed straight to the
     * existing secure endpoint and never stored, cached or logged.
     */
    public static void confirm(Context ctx, String username, String password, Callback cb) {
        final Context app = ctx.getApplicationContext();
        AppExecutors.io().execute(() -> {
            ApiResponse res;
            try {
                res = ApiClient.post(app, "v1/license/confirm",
                        new JSONObject().put("username", username).put("password", password), true);
            } catch (Exception e) {
                res = ApiResponse.offline(null);
            }
            LicenseState state = LicenseState.from(res);
            if (res.success) state.confirmed = true;
            cache(app, state);
            main(cb, state);
        });
    }

    // ----------------------------------------------------------- app config

    /**
     * Pre-session bootstrap: the support/WhatsApp number and the refresh
     * interval published by the Super Admin. Never hard-coded in the app.
     */
    public static void syncAppConfig(Context ctx) {
        final Context app = ctx.getApplicationContext();
        AppExecutors.io().execute(() -> {
            try {
                ApiResponse res = ApiClient.get(app, "v1/app/config", null, false);
                if (!res.success || res.data == null) return;
                AppPrefs prefs = AppPrefs.get(app);
                JSONObject support = res.data.optJSONObject("support");
                if (support != null) {
                    String number = support.optString("whatsapp", "");
                    if (!number.isEmpty()) prefs.setSupportWhatsapp(number);
                }
                int minutes = res.data.optInt("sync_minutes", 0);
                if (minutes > 0) prefs.setLicenseSyncMinutes(minutes);
            } catch (Exception ignored) {
                // Config is a nice-to-have; the cached values keep working.
            }
        });
    }

    // ------------------------------------------------------------- helpers

    /**
     * Caches the answer for UX only (banner text, expiry label, routing hint).
     * Authorisation itself always requires a fresh server answer.
     */
    private static void cache(Context app, LicenseState state) {
        if (state == null || !state.networkOk) return;
        AppPrefs prefs = AppPrefs.get(app);
        prefs.setLastLicenseCheck(System.currentTimeMillis());
        if (LicenseState.UNKNOWN.equals(state.state)) return;
        prefs.setLicenseState(state.state);
        prefs.setLicenseMessage(state.message == null ? "" : state.message);
        if (state.success) {
            prefs.setLicenseDurationLabel(state.durationLabel);
            prefs.setLicenseExpiresAt(state.expiresAtMs);
            prefs.setLicenseDaysLeft(state.daysLeft);
            prefs.setLicenseConfirmed(state.confirmed);
            prefs.setLastLicenseSuccessAt(System.currentTimeMillis());
        }
        if (!state.unlocked) prefs.setLicenseConfirmed(state.confirmed);
    }

    private static void main(Callback cb, LicenseState state) {
        if (cb == null) return;
        AppExecutors.main().post(() -> cb.onResult(state));
    }
}
