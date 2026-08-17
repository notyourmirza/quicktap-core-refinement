package com.quicktap.pos.util;

import android.content.Context;
import android.net.Uri;

import com.quicktap.pos.data.AppDatabase;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Manual backup / restore of the whole Room database file. The user picks a
 * destination with the Storage Access Framework, so no storage permission is needed.
 */
public final class BackupUtil {

    private BackupUtil() { }

    public static String suggestedFileName() {
        return "quicktap_backup_" + DateUtil.fileStamp(System.currentTimeMillis()) + ".db";
    }

    public static void backupTo(Context context, Uri target) throws IOException {
        AppDatabase.get(context).query("PRAGMA wal_checkpoint(FULL)", null).close();
        File dbFile = context.getDatabasePath(AppDatabase.NAME);
        try (InputStream in = new FileInputStream(dbFile);
             OutputStream out = context.getContentResolver().openOutputStream(target)) {
            if (out == null) throw new IOException("Cannot open backup destination");
            copy(in, out);
        }
    }

    public static void restoreFrom(Context context, Uri source) throws IOException {
        AppDatabase.closeInstance();
        File dbFile = context.getDatabasePath(AppDatabase.NAME);
        deleteQuietly(new File(dbFile.getPath() + "-wal"));
        deleteQuietly(new File(dbFile.getPath() + "-shm"));
        try (InputStream in = context.getContentResolver().openInputStream(source);
             OutputStream out = new FileOutputStream(dbFile)) {
            if (in == null) throw new IOException("Cannot open backup file");
            copy(in, out);
        }
        AppDatabase.get(context); // re-open
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        out.flush();
    }

    private static void deleteQuietly(File file) {
        if (file.exists()) { //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }
}
