package com.quicktap.pos.backup;

import android.accounts.Account;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.quicktap.pos.data.AppDatabase;
import com.quicktap.pos.net.ApiClient;
import com.quicktap.pos.util.AppExecutors;
import com.quicktap.pos.util.AppPrefs;
import com.quicktap.pos.util.DateUtil;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Google Drive backup of the local SQLite database.
 *
 * The file is stored in the app's private Drive "appDataFolder", so it never
 * clutters the owner's Drive and no other app can read it. Every successful
 * upload is registered with the API (/v1/backup/register) so the Super Admin
 * panel can show backup health per shop.
 */
public final class DriveBackupManager {

    /** Private per-app Drive folder — the least-privilege Drive scope. */
    private static final String SCOPE = "oauth2:https://www.googleapis.com/auth/drive.appdata";
    private static final String UPLOAD_URL =
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,size";

    public interface Callback { void onResult(boolean ok, String message); }

    private DriveBackupManager() { }

    // ------------------------------------------------------------ sign-in

    public static GoogleSignInClient signInClient(Context context) {
        GoogleSignInOptions options = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope("https://www.googleapis.com/auth/drive.appdata"))
                .build();
        return GoogleSignIn.getClient(context, options);
    }

    public static Intent signInIntent(Context context) {
        return signInClient(context).getSignInIntent();
    }

    public static boolean isConnected(Context context) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
        return account != null && account.getAccount() != null;
    }

    public static String connectedEmail(Context context) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
        return account == null ? null : account.getEmail();
    }

    public static void disconnect(Context context, Callback cb) {
        signInClient(context).signOut().addOnCompleteListener(task -> {
            AppPrefs.get(context).setDriveBackupEnabled(false);
            if (cb != null) cb.onResult(true, "Google Drive disconnected");
        });
    }

    // ------------------------------------------------------------- backup

    /** Weekly automatic backup interval. */
    public static final long WEEK_MS = 7L * 24 * 60 * 60 * 1000;

    /**
     * Runs the weekly backup only when the last one is older than a week.
     * Older copies are deleted, so Drive always holds exactly one - the newest.
     */
    public static void backupWeeklyIfDue(Context context, Callback cb) {
        AppPrefs prefs = AppPrefs.get(context);
        if (!prefs.isWeeklyBackupEnabled() || !isConnected(context)) {
            finish(cb, false, "Weekly backup is off");
            return;
        }
        if (System.currentTimeMillis() - prefs.getLastBackupAt() < WEEK_MS) {
            finish(cb, true, "Backup is up to date");
            return;
        }
        backupNow(context, cb);
    }

    /** Uploads the current database to Drive and registers it with the API. */
    public static void backupNow(Activity activity, Callback cb) {
        backupNow(activity.getApplicationContext(), cb);
    }

    public static void backupNow(Context context, Callback cb) {
        Context app = context.getApplicationContext();
        AppExecutors.io().execute(() -> {
            try {
                GoogleSignInAccount signIn = GoogleSignIn.getLastSignedInAccount(app);
                Account account = signIn == null ? null : signIn.getAccount();
                if (account == null) {
                    finish(cb, false, "Connect a Google account first");
                    return;
                }
                if (!ApiClient.isOnline(app)) {
                    finish(cb, false, "No internet connection");
                    return;
                }

                String token = GoogleAuthUtil.getToken(app, account, SCOPE);
                File db = prepareDatabaseFile(app);
                String name = "quicktap_backup_" + DateUtil.fileStamp(System.currentTimeMillis()) + ".db";

                JSONObject uploaded = upload(token, name, db);
                if (uploaded == null) {
                    finish(cb, false, "Drive upload failed");
                    return;
                }

                // Only one backup is kept: drop the copy this one replaces.
                String previous = AppPrefs.get(app).getLastBackupFileId();
                if (previous != null && !previous.isEmpty()
                        && !previous.equals(uploaded.optString("id", ""))) {
                    deleteRemote(token, previous);
                }

                AppPrefs prefs = AppPrefs.get(app);
                prefs.setDriveBackupEnabled(true);
                prefs.setLastBackupAt(System.currentTimeMillis());
                prefs.setLastBackupFileId(uploaded.optString("id", ""));

                register(app, uploaded, name, db.length());
                finish(cb, true, "Backup uploaded to Google Drive");
            } catch (Exception e) {
                finish(cb, false, "Backup failed: " + e.getMessage());
            }
        });
    }

    private static File prepareDatabaseFile(Context context) {
        AppDatabase.get(context).query("PRAGMA wal_checkpoint(FULL)", null).close();
        return context.getDatabasePath(AppDatabase.NAME);
    }

    private static JSONObject upload(String token, String name, File file) throws Exception {
        String boundary = "qtpos" + System.currentTimeMillis();
        JSONObject metadata = new JSONObject()
                .put("name", name)
                .put("parents", new org.json.JSONArray().put("appDataFolder"));

        HttpURLConnection conn = (HttpURLConnection) new URL(UPLOAD_URL).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(120000);
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Content-Type", "multipart/related; boundary=" + boundary);

            try (OutputStream out = conn.getOutputStream()) {
                write(out, "--" + boundary + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n");
                write(out, metadata.toString() + "\r\n");
                write(out, "--" + boundary + "\r\nContent-Type: application/octet-stream\r\n\r\n");
                try (InputStream in = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                }
                write(out, "\r\n--" + boundary + "--\r\n");
                out.flush();
            }

            int code = conn.getResponseCode();
            if (code >= 400) return null;
            StringBuilder sb = new StringBuilder();
            try (java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            return new JSONObject(sb.toString());
        } finally {
            conn.disconnect();
        }
    }

    /** Removes a superseded backup file so Drive only ever holds the latest one. */
    private static void deleteRemote(String token, String fileId) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(
                    "https://www.googleapis.com/drive/v3/files/" + fileId).openConnection();
            conn.setRequestMethod("DELETE");
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.getResponseCode();
            conn.disconnect();
        } catch (Exception ignored) {
            // A stale copy left behind is harmless; the newest one is registered.
        }
    }

    private static void register(Context ctx, JSONObject uploaded, String name, long size) {
        try {
            ApiClient.post(ctx, "v1/backup/register", new JSONObject()
                    .put("provider", "google_drive")
                    .put("file_id", uploaded.optString("id", ""))
                    .put("file_name", name)
                    .put("size_bytes", size)
                    .put("account_email", connectedEmail(ctx)), true);
        } catch (Exception ignored) {
            // Registration is telemetry only — the backup itself already succeeded.
        }
    }

    private static void write(OutputStream out, String value) throws Exception {
        out.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void finish(Callback cb, boolean ok, String message) {
        if (cb != null) AppExecutors.main().post(() -> cb.onResult(ok, message));
    }
}
