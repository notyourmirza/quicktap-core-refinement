package com.quicktap.pos;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.quicktap.pos.auth.SessionManager;
import com.quicktap.pos.data.PosRepository;
import com.quicktap.pos.backup.BackupWorker;
import com.quicktap.pos.sync.SyncWorker;
import com.quicktap.pos.net.RemoteEndpoint;
import com.quicktap.pos.theme.RemoteTheme;
import com.quicktap.pos.theme.ThemeEngine;
import com.quicktap.pos.util.AppPrefs;
import com.quicktap.pos.util.SessionWatcher;

/**
 * Application entry point. Initialises prefs, theme, the singleton repository,
 * the auto session lock watcher and the weekly backup worker.
 */
public class QuickTapApp extends Application {

    private static QuickTapApp instance;
    private PosRepository repository;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        AppPrefs.get(this);
        // Resolve the API endpoint (Firebase-controlled, BuildConfig fallback)
        // before any network call is made.
        RemoteEndpoint.init(this);
        // Light / dark / system is the shop's choice and survives restarts.
        com.quicktap.pos.theme.ThemeMode.apply(this);

        // Paints every screen with the brand colour published by the admin panel.
        ThemeEngine.install(this);
        repository = new PosRepository(this);

        SessionManager.get(this);
        SessionWatcher.install(this);
        // Sync is manual by default; this only re-arms the job if the shop
        // switched auto-sync on. Backups run once a week and replace the old one.
        SyncWorker.applySchedule(this);
        BackupWorker.schedule(this);

        // Pull the latest branding on cold start so a colour change lands fast.
        RemoteTheme.refresh(this, null);
    }


    public static QuickTapApp get() { return instance; }

    public PosRepository repo() { return repository; }
}
