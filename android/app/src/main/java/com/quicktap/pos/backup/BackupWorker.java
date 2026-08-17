package com.quicktap.pos.backup;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.quicktap.pos.util.AppPrefs;

import java.util.concurrent.TimeUnit;

/**
 * Weekly automatic backup. One copy only: every run uploads a fresh database
 * snapshot and deletes the previous file, so the newest backup always replaces
 * the old one instead of piling up.
 */
public class BackupWorker extends Worker {

    private static final String UNIQUE_NAME = "quicktap-weekly-backup";

    public BackupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        if (!AppPrefs.get(ctx).isWeeklyBackupEnabled()) return Result.success();
        DriveBackupManager.backupWeeklyIfDue(ctx, null);
        return Result.success();
    }

    public static void schedule(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                BackupWorker.class, 7, TimeUnit.DAYS)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    public static void cancel(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME);
    }
}
