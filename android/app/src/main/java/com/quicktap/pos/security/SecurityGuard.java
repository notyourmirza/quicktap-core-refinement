package com.quicktap.pos.security;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Debug;
import android.util.Base64;

import java.io.File;
import java.security.MessageDigest;

/**
 * Local integrity / tamper checks.
 *
 * <p>These checks are a deterrent, never an authority: a patched APK can strip
 * them out. That is why the result is also reported to the server with every
 * licence check, and the server alone decides whether the app stays unlocked.</p>
 */
public final class SecurityGuard {

    /** SHA-256 of the release signing certificate, base64. Empty = not pinned yet. */
    private static final String EXPECTED_SIGNATURE = "";

    private static final String[] ROOT_BINARIES = {
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/app/Superuser.apk",
            "/system/xbin/daemonsu", "/data/local/bin/su", "/data/local/xbin/su",
            "/system/app/Magisk.apk", "/sbin/magisk", "/data/adb/magisk"
    };

    private static final String[] HOOK_PACKAGES = {
            "de.robv.android.xposed.installer", "com.saurik.substrate",
            "com.topjohnwu.magisk", "com.koushikdutta.rommanager",
            "com.android.vending.billing.InAppBillingService.LUCK", "com.chelpus.lackypatch",
            "com.dimonvideo.luckypatcher", "com.forpda.lp", "com.android.vending.billing.InAppBillingService.CLON"
    };

    private SecurityGuard() { }

    /** True when anything suspicious was detected on this device/build. */
    public static boolean isCompromised(Context context) {
        return isSignatureInvalid(context)
                || isRooted()
                || hasHookingApp(context)
                || isDebuggerAttached()
                || isDebuggableBuild(context)
                || isEmulator();
    }

    /**
     * Compact report string sent to the server with licence checks, e.g.
     * "root,debug". Empty string means clean.
     */
    public static String report(Context context) {
        StringBuilder sb = new StringBuilder();
        if (isSignatureInvalid(context)) append(sb, "signature");
        if (isRooted()) append(sb, "root");
        if (hasHookingApp(context)) append(sb, "hook");
        if (isDebuggerAttached()) append(sb, "debugger");
        if (isDebuggableBuild(context)) append(sb, "debuggable");
        if (isEmulator()) append(sb, "emulator");
        return sb.toString();
    }

    private static void append(StringBuilder sb, String flag) {
        if (sb.length() > 0) sb.append(',');
        sb.append(flag);
    }

    /** Re-signed APK detection. Skipped until EXPECTED_SIGNATURE is filled in. */
    public static boolean isSignatureInvalid(Context context) {
        if (EXPECTED_SIGNATURE.isEmpty()) return false;
        String actual = signatureHash(context);
        return actual == null || !EXPECTED_SIGNATURE.equals(actual);
    }

    /** SHA-256 of the signing certificate — print it once to pin the release build. */
    public static String signatureHash(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            String pkg = context.getPackageName();
            Signature[] signatures;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageInfo info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES);
                signatures = info.signingInfo != null
                        ? info.signingInfo.getApkContentsSigners() : null;
            } else {
                @SuppressWarnings("deprecation")
                PackageInfo info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
                signatures = info.signatures;
            }
            if (signatures == null || signatures.length == 0) return null;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(signatures[0].toByteArray());
            return Base64.encodeToString(md.digest(), Base64.NO_WRAP);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean isRooted() {
        for (String path : ROOT_BINARIES) {
            try {
                if (new File(path).exists()) return true;
            } catch (Throwable ignored) { }
        }
        String tags = Build.TAGS;
        return tags != null && tags.contains("test-keys");
    }

    public static boolean hasHookingApp(Context context) {
        PackageManager pm = context.getPackageManager();
        for (String pkg : HOOK_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0);
                return true;
            } catch (PackageManager.NameNotFoundException ignored) {
            } catch (Throwable ignored) { }
        }
        return false;
    }

    public static boolean isDebuggerAttached() {
        return Debug.isDebuggerConnected() || Debug.waitingForDebugger();
    }

    public static boolean isDebuggableBuild(Context context) {
        try {
            return (context.getApplicationInfo().flags
                    & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
                    && !com.quicktap.pos.BuildConfig.DEBUG;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isEmulator() {
        String fp = String.valueOf(Build.FINGERPRINT);
        String model = String.valueOf(Build.MODEL);
        return fp.startsWith("generic") || fp.contains("vbox") || fp.contains("test-keys")
                || model.contains("Emulator") || model.contains("Android SDK built for x86")
                || "Genymotion".equals(Build.MANUFACTURER)
                || String.valueOf(Build.PRODUCT).contains("sdk_gphone");
    }
}
