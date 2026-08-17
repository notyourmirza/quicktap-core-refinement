package com.quicktap.pos.auth;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import com.quicktap.pos.util.AppPrefs;

import java.security.MessageDigest;
import java.util.UUID;

/**
 * Stable per-installation device identity used for server-side device binding.
 *
 * The raw id is derived from ANDROID_ID plus hardware markers and hashed, so the
 * server only ever stores an opaque fingerprint (it never sees the raw id).
 */
public final class DeviceIdentity {

    private DeviceIdentity() { }

    @SuppressLint("HardwareIds")
    public static synchronized String id(Context context) {
        AppPrefs prefs = AppPrefs.get(context);
        String cached = prefs.getDeviceId();
        if (cached != null && !cached.isEmpty()) return cached;

        String androidId = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ANDROID_ID);
        String seed = (androidId == null || androidId.isEmpty() ? UUID.randomUUID().toString() : androidId)
                + '|' + Build.MANUFACTURER + '|' + Build.MODEL + '|' + Build.DEVICE;

        String id = sha256(seed);
        prefs.setDeviceId(id);
        return id;
    }

    public static String name() {
        return Build.MANUFACTURER + " " + Build.MODEL;
    }

    public static String osVersion() {
        return "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
    }

    private static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(value.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return UUID.nameUUIDFromBytes(value.getBytes()).toString();
        }
    }
}
