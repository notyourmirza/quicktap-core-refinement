package com.quicktap.pos.license;

import com.quicktap.pos.BuildConfig;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Thin HTTP client for the Super Admin licence server. This is the ONLY part of
 * the app that talks to a network; all business data stays on the device.
 *
 * Expected endpoints (JSON in, JSON out) — self-hosted PHP API, see php-api/README.md:
 *   POST {base}license_login.php       {email, password, device_id, device_name, app_version}
 *   POST {base}license_check.php       {device_id, token}
 *   POST {base}license_activate.php    {device_id, code}
 *
 * Expected response shape:
 *   {"success":true,"status":"APPROVED","message":"...","token":"...","expires_at":1767225600000}
 */
public final class LicenseApi {

    private static final int TIMEOUT_MS = 15000;

    private LicenseApi() { }

    public static LicenseResult login(String email, String password, String deviceId,
                                      String deviceName, String appVersion) {
        try {
            JSONObject body = new JSONObject();
            body.put("email", email);
            body.put("password", password);
            body.put("device_id", deviceId);
            body.put("device_name", deviceName);
            body.put("app_version", appVersion);
            return post("license_login.php", body);
        } catch (Exception e) {
            return offline(e);
        }
    }

    public static LicenseResult check(String deviceId, String token) {
        try {
            JSONObject body = new JSONObject();
            body.put("device_id", deviceId);
            body.put("token", token);
            return post("license_check.php", body);
        } catch (Exception e) {
            return offline(e);
        }
    }

    public static LicenseResult activate(String deviceId, String code) {
        try {
            JSONObject body = new JSONObject();
            body.put("device_id", deviceId);
            body.put("code", code);
            return post("license_activate.php", body);
        } catch (Exception e) {
            return offline(e);
        }
    }

    private static LicenseResult post(String path, JSONObject body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection)
                new URL(BuildConfig.LICENSE_BASE_URL + path).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("X-Api-Key", BuildConfig.API_KEY);

            try (OutputStream out = conn.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String raw = read(stream);
            JSONObject json = new JSONObject(raw.isEmpty() ? "{}" : raw);

            LicenseResult result = new LicenseResult();
            result.networkOk = true;
            result.success = json.optBoolean("success", code < 400);
            result.status = json.optString("status", result.success ? "APPROVED" : "PENDING");
            result.message = json.optString("message", "");
            result.token = json.optString("token", null);
            result.expiresAt = json.optLong("expires_at", 0L);
            return result;
        } finally {
            conn.disconnect();
        }
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private static LicenseResult offline(Exception e) {
        LicenseResult result = new LicenseResult();
        result.networkOk = false;
        result.success = false;
        result.message = e.getMessage() == null ? "Network unavailable" : e.getMessage();
        return result;
    }
}
