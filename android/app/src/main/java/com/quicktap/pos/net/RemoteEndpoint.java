package com.quicktap.pos.net;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.quicktap.pos.BuildConfig;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Endpoint resolution for the whole app — the ONE routing mechanism.
 *
 * <pre>
 *   Android
 *     -> fixed bootstrap URL (BuildConfig.BOOTSTRAP_BASE_URL)
 *     -> Firebase configuration published by the Super Admin
 *     -> Firebase Remote Config
 *     -> api_base_url
 *     -> ApiClient
 *     -> self-hosted PHP API
 * </pre>
 *
 * <p>Firebase is used for remote configuration only. It never decides whether a
 * licence is valid — that answer always comes from the server, which is the
 * single licence authority.</p>
 *
 * <p>Resolution order for the API base URL:</p>
 * <ol>
 *   <li>value fetched from Firebase Remote Config ({@code api_base_url})</li>
 *   <li>last known-good value cached from a previous run (works offline)</li>
 *   <li>the compiled-in bootstrap URL (always a working fallback)</li>
 * </ol>
 *
 * <p>Only absolute HTTPS URLs are ever accepted, so a broken or hostile Remote
 * Config value can never downgrade the transport or strand an installation:
 * the last known-good configuration keeps working.</p>
 *
 * <p>Firebase is accessed reflectively so the app still compiles and runs when
 * the Firebase SDK is not on the classpath.</p>
 */
public final class RemoteEndpoint {

    private static final String TAG = "RemoteEndpoint";
    private static final String PREFS = "remote_endpoint";
    private static final String KEY_API = "api_base_url";
    private static final String KEY_FETCHED_AT = "fetched_at";

    // Firebase configuration (non-secret client identifiers) served by the
    // bootstrap endpoint so the Super Admin can move projects without a rebuild.
    private static final String KEY_FB_APP_ID = "fb_app_id";
    private static final String KEY_FB_API_KEY = "fb_api_key";
    private static final String KEY_FB_PROJECT = "fb_project_id";
    private static final String KEY_FB_SENDER = "fb_sender_id";

    /** Minimum interval between Remote Config fetches (seconds). */
    private static final long MIN_FETCH_INTERVAL = 3600L;
    private static final int TIMEOUT_MS = 10000;

    private static volatile String apiBase;
    private static volatile boolean initialised;

    private RemoteEndpoint() { }

    /** Call once from Application.onCreate(). Cheap and never blocks the UI. */
    public static void init(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        apiBase = normalise(sp.getString(KEY_API, null));
        initialised = true;
        fetchAsync(app);
    }

    /** Base URL for the REST API, always with a trailing slash. */
    public static String apiBase() {
        return apiBase != null && !apiBase.isEmpty() ? apiBase : normalise(null);
    }

    /** Fixed bootstrap endpoint — never remote controlled. */
    public static String bootstrapBase() {
        String v = BuildConfig.BOOTSTRAP_BASE_URL;
        return v.endsWith("/") ? v : v + "/";
    }

    public static boolean isReady() {
        return initialised;
    }

    /** Refreshes the endpoint in the background; safe to call repeatedly. */
    public static void fetchAsync(final Context context) {
        final Context app = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject firebase = fetchBootstrap(app);
                    fetchRemoteConfig(app, firebase);
                } catch (Throwable t) {
                    Log.d(TAG, "remote config unavailable: " + t.getClass().getSimpleName());
                }
            }
        }, "remote-endpoint").start();
    }

    // ------------------------------------------------------------ bootstrap

    /**
     * Reads the Firebase configuration from the fixed bootstrap API and caches
     * it. Returns the cached configuration when the network is unavailable.
     */
    private static JSONObject fetchBootstrap(Context app) {
        SharedPreferences sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        HttpURLConnection conn = null;
        try {
            URL url = new URL(bootstrapBase() + "v1/app/bootstrap");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("X-App-Id", BuildConfig.APP_ID);
            conn.setRequestProperty("X-Api-Key", BuildConfig.API_KEY);
            conn.setRequestProperty("X-App-Version", BuildConfig.VERSION_NAME);

            int code = conn.getResponseCode();
            if (code >= 400) return cachedFirebase(sp);
            JSONObject root = new JSONObject(read(conn.getInputStream()));
            JSONObject data = root.optJSONObject("data");
            if (data == null) return cachedFirebase(sp);

            JSONObject fb = data.optJSONObject("firebase");
            if (fb != null && isUsableFirebase(fb)) {
                sp.edit()
                        .putString(KEY_FB_APP_ID, fb.optString("app_id"))
                        .putString(KEY_FB_API_KEY, fb.optString("api_key"))
                        .putString(KEY_FB_PROJECT, fb.optString("project_id"))
                        .putString(KEY_FB_SENDER, fb.optString("sender_id"))
                        .apply();
            }
            // Safety net: the bootstrap API may also publish the API base URL
            // directly, used only when it is a valid HTTPS endpoint.
            applyApiBase(app, data.optString("api_base_url", ""));
            return cachedFirebase(sp);
        } catch (Exception e) {
            return cachedFirebase(sp);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static JSONObject cachedFirebase(SharedPreferences sp) {
        try {
            JSONObject fb = new JSONObject()
                    .put("app_id", sp.getString(KEY_FB_APP_ID, ""))
                    .put("api_key", sp.getString(KEY_FB_API_KEY, ""))
                    .put("project_id", sp.getString(KEY_FB_PROJECT, ""))
                    .put("sender_id", sp.getString(KEY_FB_SENDER, ""));
            return isUsableFirebase(fb) ? fb : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isUsableFirebase(JSONObject fb) {
        return fb.optString("app_id", "").length() > 3
                && fb.optString("api_key", "").length() > 3
                && fb.optString("project_id", "").length() > 1;
    }

    // -------------------------------------------------------- remote config

    /**
     * Initialises Firebase with the published configuration (or the bundled
     * google-services.json when present) and reads {@code api_base_url}.
     */
    private static void fetchRemoteConfig(Context app, JSONObject firebase) throws Exception {
        Class<?> rcClass = Class.forName("com.google.firebase.remoteconfig.FirebaseRemoteConfig");
        Class<?> firebaseApp = Class.forName("com.google.firebase.FirebaseApp");

        Object instance = null;
        if (firebase != null) {
            instance = initWithOptions(app, firebaseApp, firebase);
        }
        if (instance == null) {
            Method initApp = firebaseApp.getMethod("initializeApp", Context.class);
            instance = initApp.invoke(null, app);
        }
        if (instance == null) return; // no Firebase configuration at all

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
        applyApiBase(app, (String) getString.invoke(rc, KEY_API));
    }

    /** Builds a FirebaseApp from server-published options (reflection only). */
    private static Object initWithOptions(Context app, Class<?> firebaseApp, JSONObject fb) {
        try {
            Method getApps = firebaseApp.getMethod("getApps", Context.class);
            Object existing = getApps.invoke(null, app);
            if (existing instanceof java.util.List && !((java.util.List<?>) existing).isEmpty()) {
                return ((java.util.List<?>) existing).get(0);
            }
            Class<?> optionsBuilder = Class.forName("com.google.firebase.FirebaseOptions$Builder");
            Object b = optionsBuilder.getConstructor().newInstance();
            optionsBuilder.getMethod("setApplicationId", String.class)
                    .invoke(b, fb.optString("app_id"));
            optionsBuilder.getMethod("setApiKey", String.class)
                    .invoke(b, fb.optString("api_key"));
            optionsBuilder.getMethod("setProjectId", String.class)
                    .invoke(b, fb.optString("project_id"));
            String sender = fb.optString("sender_id", "");
            if (!sender.isEmpty()) {
                optionsBuilder.getMethod("setGcmSenderId", String.class).invoke(b, sender);
            }
            Object options = optionsBuilder.getMethod("build").invoke(b);
            return firebaseApp.getMethod("initializeApp", Context.class,
                            Class.forName("com.google.firebase.FirebaseOptions"))
                    .invoke(null, app, options);
        } catch (Throwable t) {
            Log.d(TAG, "firebase options unavailable: " + t.getClass().getSimpleName());
            return null;
        }
    }

    // ---------------------------------------------------------------- store

    /** Stores a fetched base URL; blanks and non-HTTPS values are rejected. */
    private static void applyApiBase(Context app, String url) {
        if (!isUsable(url)) return;
        String value = normalise(url);
        apiBase = value;
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_API, value)
                .putLong(KEY_FETCHED_AT, System.currentTimeMillis())
                .apply();
    }

    /** Only absolute HTTPS endpoints are accepted — no downgrade to plain HTTP. */
    private static boolean isUsable(String url) {
        return url != null && url.length() > 8 && url.startsWith("https://");
    }

    private static String normalise(String value) {
        String v = isUsable(value) ? value : BuildConfig.BOOTSTRAP_BASE_URL;
        if (v == null || v.isEmpty()) return "";
        return v.endsWith("/") ? v : v + "/";
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}
