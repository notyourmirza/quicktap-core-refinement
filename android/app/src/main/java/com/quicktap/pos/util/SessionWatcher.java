package com.quicktap.pos.util;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.quicktap.pos.auth.SessionManager;
import com.quicktap.pos.sync.SyncEngine;
import com.quicktap.pos.ui.LoginActivity;
import com.quicktap.pos.ui.UnlockActivity;

/**
 * Application-wide guard that implements the auto session lock.
 *
 * It tracks how long the app has been in the background / idle and, when the
 * configured timeout has passed, pushes the {@link UnlockActivity} in front of
 * whatever screen the cashier left open. It also kicks a sync every time the
 * app comes back to the foreground.
 */
public class SessionWatcher implements Application.ActivityLifecycleCallbacks {

    private final Application app;
    private int started;
    private boolean unlockVisible;

    public SessionWatcher(Application app) { this.app = app; }

    public static void install(Application app) {
        app.registerActivityLifecycleCallbacks(new SessionWatcher(app));
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        boolean cameToForeground = started == 0;
        started++;

        unlockVisible = activity instanceof UnlockActivity;
        if (unlockVisible || activity instanceof LoginActivity) return;

        SessionManager session = SessionManager.get(app);
        if (session.shouldLock()) {
            activity.startActivity(new Intent(activity, UnlockActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP));
            return;
        }
        session.markActivity();
        if (cameToForeground) SyncEngine.syncAuto(app);
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        started = Math.max(0, started - 1);
        if (started == 0 && !unlockVisible) {
            // Remember when the app went idle so the timeout is measured correctly.
            SessionManager.get(app).markActivity();
        }
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        if (!(activity instanceof UnlockActivity)) SessionManager.get(app).markActivity();
    }

    @Override public void onActivityCreated(@NonNull Activity a, @Nullable Bundle b) { }
    @Override public void onActivityPaused(@NonNull Activity a) { }
    @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) { }
    @Override public void onActivityDestroyed(@NonNull Activity a) { }
}
