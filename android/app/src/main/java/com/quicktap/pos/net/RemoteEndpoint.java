package com.quicktap.pos.net;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.quicktap.pos.BuildConfig;

import java.lang.reflect.Method;

/**
 * Remote endpoint configuration.
 *
 * <p>Firebase Remote Config is used for ONE purpose only: telling the installed
 * app which server to talk to (api_base_url / license_base_url). It never
 * decides whether a licence is valid — that answer always comes from the
 * server, which is the single authority.</p>
 *
 * <p>Resolution order for the base URL:</p>
 * <ol>
 *   <li>value fetched from Firebase Remote Config (cached in prefs)</li>
 *   <li>last cached value from a previous run (works offline)</li>
 *   <li>the compiled-in BuildConfig value (always a working fallback)</li>
 * </ol>
 *
 * <p>Firebase is accessed reflectively so the app still compiles and runs when
 * the Firebase dependency / google-services.json has not been added yet. Drop
 * google-services.json into <code>android/app/</code> and the fetch activates
 * automatically — no code change required.</p>
 */
public final class RemoteEndpoint {

    private static final String TAG = "RemoteEndpoint";
    private static final String PREFS = "remote_endpoint";
    private static final String KEY_API = "api_base_url";
    private static final String KEY_LICENSE = "license_base_url";
    private static final String KEY_FETCHED_AT = "fetched_at";

    /** Minimum interval between Remote Config fetches (seconds). */
    private static final long MIN_FETCH_INTERVAL = 3600L;

    private static volatile String apiBase;
    private static volatile String licenseBase;
    private static volatile boolean initialised;

    private RemoteEndpoint() { }

    /** Call once from Application.onCreate(). Cheap and never blocks the UI. */
    public static void init(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        apiBase = normalise(sp.getString(KEY_API, null), BuildConfig.API_BASE_URL);
        licenseBase = normalise(sp.getString(KEY_LICENSE, null), BuildConfig.LICENSE_BASE_URL);
        initialised = true;
        fetchAsync(app);
    }

    /** Base URL for the v2 REST API, always with a trailing slash. */
    public static String apiBase() {
        return apiBase != null ? apiBase : normalise(null, BuildConfig.API_BASE_URL);
    }

    /** Base URL for the legacy licence API, always with a trailing slash. */
    public static String licenseBase() {
        return licenseBase != null ? licenseBase : normalise(null, BuildConfig.LICENSE_BASE_URL);
    }

    public static boolean isReady() {
        return initialised;
    }

    /** Refreshes the endpoints in the background; safe to call repeatedly. */
    public static void fetchAsync(final Context context) {
        final Context app = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    fetchBlocking(app);
                } catch (Throwable t) {
                    Log.d(TAG, "remote config unavailable: " + t.getClass().getSimpleName());
                }
            }
        }, "remote-endpoint").start();
    }

    private static void fetchBlocking(Context app) throws Exception {
        Class<?> rcClass = Class.forName("com.google.firebase.remoteconfig.FirebaseRemoteConfig");

        // FirebaseApp.initializeApp(context) is a no-op when already initialised
        // and returns null when google-services.json is absent.
        Class<?> firebaseApp = Class.forName("com.google.firebase.FirebaseApp");
        Method initApp = firebaseApp.getMethod("initializeApp", Context.class);
        if (initApp.invoke(null, app) == null) {
            return;
        }

        Object rc = rcClass.getMethod("getInstance").invoke(null);
        if (rc == null) return;

        Class<?> settingsBuilder =
                Class.forName("com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings$Builder");
        Object builder = settingsBuilder.getConstructor().newInstance();
        settingsBuilder.getMethod("setMinimumFetchIntervalInSeconds", long.class)
                .invoke(builder, MIN_FETCH_INTERVAL);
        Object settings = settingsBuilder.getMethod("build").invoke(builder);
        rcClass.getMethod("setConfigSettingsAsync",
                Class.forName("com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings"))
                .invoke(rc, settings);

        Object task = rcClass.getMethod("fetchAndActivate").invoke(rc);
        Class<?> tasks = Class.forName("com.google.android.gms.tasks.Tasks");
        tasks.getMethod("await", Class.forName("com.google.android.gms.tasks.Task"))
                .invoke(null, task);

        Method getString = rcClass.getMethod("getString", String.class);
        String api = (String) getString.invoke(rc, KEY_API);
        String license = (String) getString.invoke(rc, KEY_LICENSE);
        apply(app, api, license);
    }

    /** Stores fetched values, ignoring blanks and anything that is not https. */
    private static void apply(Context app, String api, String license) {
        SharedPreferences.Editor ed =
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        boolean changed = false;

        if (isUsable(api)) {
            apiBase = normalise(api, BuildConfig.API_BASE_URL);
            ed.putString(KEY_API, apiBase);
            changed = true;
        }
        if (isUsable(license)) {
            licenseBase = normalise(license, BuildConfig.LICENSE_BASE_URL);
            ed.putString(KEY_LICENSE, licenseBase);
            changed = true;
        }
        if (changed) {
            ed.putLong(KEY_FETCHED_AT, System.currentTimeMillis()).apply();
        }
    }

    /** Only absolute HTTPS endpoints are accepted — no downgrade to plain HTTP. */
    private static boolean isUsable(String url) {
        return url != null && url.length() > 8 && url.startsWith("https://");
    }

    private static String normalise(String value, String fallback) {
        String v = isUsable(value) ? value : fallback;
        if (v == null || v.isEmpty()) return "";
        return v.endsWith("/") ? v : v + "/";
    }
}
