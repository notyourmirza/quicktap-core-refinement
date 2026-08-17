package com.quicktap.pos.sync;

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
 * Background sync worker.
 *
 * Automatic syncing is DISABLED by default — the repeated unattended runs were
 * re-uploading already-backed-up rows and duplicating data. Sync now happens
 * when the user presses "Sync now" in Settings. The periodic job is only
 * scheduled when the shop deliberately switches auto-sync back on.
 */
public class SyncWorker extends Worker {

    private static final String UNIQUE_NAME = "quicktap-auto-sync";

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        if (!AppPrefs.get(ctx).isAutoSyncEnabled()) {
            cancel(ctx);
            return Result.success();
        }
        boolean ok = SyncEngine.runBlocking(ctx)[0];
        return ok ? Result.success() : Result.retry();
    }

    /** Applies the current auto-sync preference; cancels the job when it is off. */
    public static void applySchedule(Context context) {
        if (!AppPrefs.get(context).isAutoSyncEnabled()) {
            cancel(context);
            return;
        }
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                SyncWorker.class, 6, TimeUnit.HOURS)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request);
    }

    public static void cancel(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME);
    }
}
