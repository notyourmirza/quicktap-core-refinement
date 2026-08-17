package com.quicktap.pos.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.net.URLEncoder;

/**
 * Single place that owns "how do we reach the administrator".
 * Everything goes to WhatsApp; the number is published by the Super Admin
 * (app_settings.support_whatsapp) and cached locally.
 */
public final class SupportContact {

    private SupportContact() { }

    /** Digits-only MSISDN in international format, no plus sign. Empty when the
     *  Super Admin has not published one — never hard-coded in the app. */
    public static String number(Context context) {
        String raw = AppPrefs.get(context).getSupportWhatsapp();
        return raw == null ? "" : raw.replaceAll("[^0-9]", "");
    }

    /** True once the server config supplied a usable support number. */
    public static boolean available(Context context) {
        return number(context).length() >= 6;
    }

    public static String display(Context context) {
        return available(context) ? "+" + number(context) : "Not available";
    }

    /** Opens WhatsApp with a prefilled message; falls back to the browser. */
    public static void chat(Context context, String message) {
        if (!available(context)) {
            // Graceful, never a crash: the number comes from the server config.
            copy(context, message);
            Toast.makeText(context,
                    "Support number is not available yet. Please try again shortly.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        String url = "https://wa.me/" + number(context) + "?text=" + encode(message);
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            copy(context, message);
            Toast.makeText(context, "WhatsApp not available. Message copied.",
                    Toast.LENGTH_LONG).show();
        }
    }

    public static void copy(Context context, String text) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("QuickTap", text));
    }

    /** Standard signature appended to every outgoing request. */
    public static String signature(Context context) {
        AppPrefs prefs = AppPrefs.get(context);
        return "\n\n— — —\nStore: " + prefs.getStoreName()
                + "\nUser: " + (prefs.getFullName() == null || prefs.getFullName().isEmpty()
                ? String.valueOf(prefs.getUsername()) : prefs.getFullName())
                + "\nDevice: " + prefs.getDeviceId()
                + "\nLicence: " + prefs.getLicenseStatus();
    }

    private static String encode(String text) {
        try {
            return URLEncoder.encode(text == null ? "" : text, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }
}
