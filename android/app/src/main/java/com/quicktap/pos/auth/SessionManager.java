package com.quicktap.pos.auth;

import android.content.Context;

import com.quicktap.pos.BuildConfig;
import com.quicktap.pos.net.ApiClient;
import com.quicktap.pos.net.ApiResponse;
import com.quicktap.pos.theme.RemoteTheme;
import com.quicktap.pos.util.AppExecutors;
import com.quicktap.pos.util.AppPrefs;

import org.json.JSONObject;

/**
 * Owns everything about "who is signed in on this device":
 * JWT access/refresh tokens, device binding, fingerprint preference,
 * the auto-lock clock and the server-driven profile snapshot.
 *
 * All network work happens on the IO executor; callbacks come back on the
 * caller's thread via {@link AppExecutors#main()}.
 */
public final class SessionManager {

    public interface Callback { void onResult(boolean ok, String message, String code); }

    private static SessionManager instance;

    private final Context app;
    private final AppPrefs prefs;

    private SessionManager(Context context) {
        this.app = context.getApplicationContext();
        this.prefs = AppPrefs.get(this.app);
    }

    public static synchronized SessionManager get(Context context) {
        if (instance == null) {
            instance = new SessionManager(context);
            // Let the HTTP layer renew expired access tokens by itself.
            ApiClient.setTokenRefresher(SessionManager::refreshBlockingStatic);
        }
        return instance;
    }

    // ---------------------------------------------------------------- state

    public boolean isSignedIn() { return prefs.getAccessToken() != null && prefs.getRefreshToken() != null; }

    public String username() { return prefs.getUsername(); }

    public String role() { return prefs.getUserRole(); }

    public boolean isFingerprintEnabled() { return prefs.isFingerprintEnabled(); }

    // ---------------------------------------------------------------- login

    public void login(String username, String password, Callback cb) {
        AppExecutors.io().execute(() -> {
            try {
                JSONObject body = new JSONObject()
                        .put("username", username)
                        .put("password", password)
                        .put("device_id", DeviceIdentity.id(app))
                        .put("device_name", DeviceIdentity.name())
                        .put("app_version", BuildConfig.VERSION_NAME)
                        .put("os_version", DeviceIdentity.osVersion());

                ApiResponse res = ApiClient.post(app, "v1/auth/login", body, false);
                if (res.success) {
                    storeSession(res.dataOrEmpty());
                    prefs.setUsername(username);
                    prefs.touchActivity();
                }
                // Hand control back to the UI immediately. Branding used to be
                // fetched here with a second blocking request, which is what
                // made the login screen sit on the spinner for so long.
                post(cb, res.success, res.message, res.errorCode);
                if (res.success) {
                    AppExecutors.io().execute(() -> RemoteTheme.refreshBlocking(app));
                }
            } catch (Exception e) {
                post(cb, false, "Sign-in failed: " + e.getMessage(), "CLIENT_ERROR");
            }
        });
    }

    /** Verifies the current user's password on the auto-lock screen. */
    public void unlockWithPassword(String password, Callback cb) {
        AppExecutors.io().execute(() -> {
            try {
                ApiResponse res = ApiClient.post(app, "v1/auth/unlock",
                        new JSONObject().put("password", password), true);
                if (res.success) prefs.touchActivity();
                post(cb, res.success, res.message, res.errorCode);
            } catch (Exception e) {
                post(cb, false, "Unlock failed", "CLIENT_ERROR");
            }
        });
    }

    /** Turns fingerprint unlock on/off both locally and on the server. */
    public void setFingerprintEnabled(boolean enabled, Callback cb) {
        prefs.setFingerprintEnabled(enabled);
        AppExecutors.io().execute(() -> {
            ApiResponse res;
            try {
                res = ApiClient.post(app, "v1/auth/fingerprint",
                        new JSONObject().put("enabled", enabled), true);
            } catch (Exception e) {
                res = ApiResponse.offline(e.getMessage());
            }
            post(cb, res.success, res.message, res.errorCode);
        });
    }

    /** Refreshes the cached profile (role, shop, plan) from /v1/auth/me. */
    public void refreshProfile(Callback cb) {
        AppExecutors.io().execute(() -> {
            ApiResponse res = ApiClient.get(app, "v1/auth/me", null, true);
            if (res.success && res.data != null) {
                prefs.setUserRole(res.data.optString("role", prefs.getUserRole()));
                prefs.setFullName(res.data.optString("full_name", ""));
                prefs.setShopName(res.data.optString("shop_name", prefs.getStoreName()));
                prefs.setFingerprintEnabled(res.data.optInt("fingerprint_enabled", 0) == 1);
            }
            post(cb, res.success, res.message, res.errorCode);
        });
    }

    public void logout(Callback cb) {
        AppExecutors.io().execute(() -> {
            ApiClient.post(app, "v1/auth/logout", new JSONObject(), true);
            prefs.clearSession();
            post(cb, true, "Signed out", "");
        });
    }

    /** Wipes the local session without contacting the server (device blocked etc.). */
    public void forceSignOut() { prefs.clearSession(); }

    /**
     * Stores the session returned by /v1/auth/register so the new account can
     * immediately poll its licence status. Same storage path as a normal login;
     * the password itself is never persisted.
     */
    public void adoptSession(JSONObject data, String username) {
        if (data == null) return;
        storeSession(data);
        if (username != null && !username.isEmpty()) prefs.setUsername(username);
        prefs.touchActivity();
    }


    // -------------------------------------------------------------- refresh

    private static boolean refreshBlockingStatic(Context ctx) {
        return get(ctx).refreshBlocking();
    }

    /** Exchanges the refresh token for a new access token. Call off the main thread. */
    public synchronized boolean refreshBlocking() {
        String refresh = prefs.getRefreshToken();
        if (refresh == null) return false;
        try {
            ApiResponse res = ApiClient.post(app, "v1/auth/refresh",
                    new JSONObject().put("refresh_token", refresh), false);
            if (!res.success) {
                // A revoked/expired refresh token means the session is truly over.
                if (res.networkOk && res.httpCode == 401) prefs.clearSession();
                return false;
            }
            storeSession(res.dataOrEmpty());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------ auto-lock

    /** Minutes of inactivity before the session lock screen appears. */
    public int autoLockMinutes() { return prefs.getAutoLockMinutes(); }

    public void setAutoLockMinutes(int minutes) { prefs.setAutoLockMinutes(minutes); }

    public void markActivity() { prefs.touchActivity(); }

    public void lockNow() { prefs.setLocked(true); }

    public void markUnlocked() {
        prefs.setLocked(false);
        prefs.touchActivity();
    }

    /** True when the app must show the lock screen before any POS screen. */
    public boolean shouldLock() {
        if (!isSignedIn()) return false;
        if (prefs.isLocked()) return true;
        int minutes = prefs.getAutoLockMinutes();
        if (minutes <= 0) return false;
        long idle = System.currentTimeMillis() - prefs.getLastActivityAt();
        return idle > minutes * 60_000L;
    }

    // ----------------------------------------------------------------- util

    private void storeSession(JSONObject data) {
        JSONObject tokens = data.optJSONObject("tokens");
        if (tokens == null) tokens = data;
        String access = tokens.optString("access_token", null);
        String refresh = tokens.optString("refresh_token", null);
        if (access != null) prefs.setAccessToken(access);
        if (refresh != null) prefs.setRefreshToken(refresh);
        prefs.setTokenExpiresAt(System.currentTimeMillis() + tokens.optLong("expires_in", 3600) * 1000L);

        JSONObject user = data.optJSONObject("user");
        if (user != null) {
            prefs.setUserRole(user.optString("role", "cashier"));
            prefs.setFullName(user.optString("full_name", ""));
            prefs.setFingerprintEnabled(user.optInt("fingerprint_enabled", 0) == 1);
        }
        JSONObject shop = data.optJSONObject("shop");
        if (shop != null) {
            prefs.setShopName(shop.optString("name", prefs.getStoreName()));
            String currency = shop.optString("currency", "");
            if (!currency.isEmpty()) prefs.setCurrency(currency);
        }
    }

    private void post(Callback cb, boolean ok, String message, String code) {
        if (cb == null) return;
        AppExecutors.main().post(() -> cb.onResult(ok, message, code));
    }
}
