package com.quicktap.pos.net;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;

import com.quicktap.pos.BuildConfig;
import com.quicktap.pos.auth.DeviceIdentity;
import com.quicktap.pos.util.AppPrefs;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

/**
 * Single HTTP entry point for the QuickTap REST API.
 *
 * Responsibilities:
 *  - attaches the app credentials (X-App-Id / X-Api-Key)
 *  - attaches the device fingerprint header used for device binding
 *  - attaches the JWT access token and transparently refreshes it once on 401
 *
 * Always call from a background thread (see {@link com.quicktap.pos.util.AppExecutors}).
 */
public final class ApiClient {

    private static final int TIMEOUT_MS = 20000;

    /** Set by SessionManager to avoid a circular dependency at class-load time. */
    public interface TokenRefresher { boolean refreshBlocking(Context context); }

    private static TokenRefresher refresher;

    public static void setTokenRefresher(TokenRefresher r) { refresher = r; }

    private ApiClient() { }

    // ------------------------------------------------------------------ //

    public static ApiResponse get(Context ctx, String path, Map<String, String> query, boolean authed) {
        return call(ctx, "GET", buildPath(path, query), null, authed, true);
    }

    public static ApiResponse post(Context ctx, String path, JSONObject body, boolean authed) {
        return call(ctx, "POST", path, body, authed, true);
    }

    public static ApiResponse delete(Context ctx, String path, boolean authed) {
        return call(ctx, "DELETE", path, null, authed, true);
    }

    public static boolean isOnline(Context ctx) {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
                return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            }
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Exception e) {
            return true; // never block a request just because the check failed
        }
    }

    // ------------------------------------------------------------------ //

    private static ApiResponse call(Context ctx, String method, String path,
                                    JSONObject body, boolean authed, boolean allowRetry) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(base() + trim(path));
            conn = (HttpURLConnection) url.openConnection();
            if (conn instanceof HttpsURLConnection) {
                ((HttpsURLConnection) conn).setSSLSocketFactory(TlsCompat.socketFactory(ctx));
            }
            conn.setRequestMethod(method);
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("X-App-Id", BuildConfig.APP_ID);
            conn.setRequestProperty("X-Api-Key", BuildConfig.API_KEY);
            conn.setRequestProperty("X-App-Version", BuildConfig.VERSION_NAME);
            conn.setRequestProperty("X-Device-Id", DeviceIdentity.id(ctx));

            if (authed) {
                String token = AppPrefs.get(ctx).getAccessToken();
                if (token != null) conn.setRequestProperty("Authorization", "Bearer " + token);
            }

            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
            }

            int http = conn.getResponseCode();
            String raw = read(http >= 400 ? conn.getErrorStream() : conn.getInputStream());
            ApiResponse res = parse(raw, http);

            // One transparent refresh + retry when the access token has expired.
            if (authed && allowRetry && http == 401 && refresher != null
                    && (res.is("TOKEN_EXPIRED") || res.is("NO_TOKEN"))
                    && refresher.refreshBlocking(ctx)) {
                conn.disconnect();
                conn = null;
                return call(ctx, method, path, body, true, false);
            }
            return res;
        } catch (Exception e) {
            return ApiResponse.offline(friendly(e));
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Turns low-level network/TLS failures into something a cashier can act on. */
    private static String friendly(Exception e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String name = t.getClass().getName();
            if (name.contains("CertPathValidatorException") || name.contains("CertificateException")
                    || name.contains("SSLHandshakeException") || name.contains("SSLPeerUnverified")) {
                return "Secure connection failed. Please check the device date & time, "
                        + "then try again on a different network.";
            }
            if (name.contains("UnknownHostException")) return "No internet connection.";
            if (name.contains("SocketTimeoutException")) return "Server took too long to respond.";
        }
        String msg = e.getMessage();
        return msg == null || msg.isEmpty() ? "Network error. Please try again." : msg;
    }

    private static ApiResponse parse(String raw, int http) {
        ApiResponse res = new ApiResponse();
        res.networkOk = true;
        res.httpCode = http;
        try {
            JSONObject json = new JSONObject(raw == null || raw.isEmpty() ? "{}" : raw);
            res.success = json.optBoolean("success", http < 400);
            res.message = json.optString("message", http < 400 ? "OK" : "Request failed");
            res.errorCode = json.optString("code", "");
            Object data = json.opt("data");
            if (data instanceof JSONObject) res.data = (JSONObject) data;
            else if (data instanceof JSONArray) res.list = (JSONArray) data;
            else if (data instanceof String && !((String) data).isEmpty()) {
                Object parsed = new JSONTokener((String) data).nextValue();
                if (parsed instanceof JSONObject) res.data = (JSONObject) parsed;
                if (parsed instanceof JSONArray) res.list = (JSONArray) parsed;
            }
        } catch (Exception e) {
            res.success = false;
            res.message = "Unexpected server response";
            res.errorCode = "BAD_RESPONSE";
        }
        return res;
    }

    private static String buildPath(String path, Map<String, String> query) {
        if (query == null || query.isEmpty()) return path;
        StringBuilder sb = new StringBuilder(path);
        sb.append(path.contains("?") ? '&' : '?');
        boolean first = true;
        for (Map.Entry<String, String> e : query.entrySet()) {
            if (e.getValue() == null) continue;
            if (!first) sb.append('&');
            first = false;
            try {
                sb.append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=')
                        .append(URLEncoder.encode(e.getValue(), "UTF-8"));
            } catch (Exception ignored) { }
        }
        return sb.toString();
    }

    private static String base() {
        // Endpoint is remote-controlled (Firebase Remote Config: api_base_url)
        // with the fixed bootstrap URL as the last known-good fallback, so the
        // backend can be moved without shipping a new APK.
        String b = RemoteEndpoint.apiBase();
        if (b == null || b.isEmpty()) b = RemoteEndpoint.bootstrapBase();
        return b.endsWith("/") ? b : b + "/";
    }

    private static String trim(String path) {
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}
