package com.quicktap.pos.theme;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

import com.quicktap.pos.util.AppPrefs;

/**
 * Day / night preference for the whole app.
 *
 * <p>Three modes are supported — light, dark and "follow the system". The choice
 * is stored locally so it survives a restart, and is applied through
 * {@link AppCompatDelegate} so switching is instant and glitch free (AppCompat
 * recreates the visible activities itself; no manual {@code recreate()} needed).
 */
public final class ThemeMode {

    public static final String LIGHT = "light";
    public static final String DARK = "dark";
    public static final String SYSTEM = "system";

    private ThemeMode() { }

    /** Current stored mode, always one of light / dark / system. */
    public static String current(Context ctx) {
        String mode = AppPrefs.get(ctx).getThemeMode();
        if (DARK.equals(mode) || SYSTEM.equals(mode)) return mode;
        return LIGHT;
    }

    /** Index used by the picker dialog: 0 light, 1 dark, 2 system. */
    public static int index(Context ctx) {
        String mode = current(ctx);
        if (DARK.equals(mode)) return 1;
        if (SYSTEM.equals(mode)) return 2;
        return 0;
    }

    public static String fromIndex(int index) {
        if (index == 1) return DARK;
        if (index == 2) return SYSTEM;
        return LIGHT;
    }

    /** Human label for the current mode. */
    public static String label(Context ctx) {
        switch (current(ctx)) {
            case DARK: return "Dark";
            case SYSTEM: return "System default";
            default: return "Light";
        }
    }

    /** Stores the mode and applies it immediately. */
    public static void set(Context ctx, String mode) {
        AppPrefs prefs = AppPrefs.get(ctx);
        prefs.setThemeMode(mode);
        prefs.setDarkMode(DARK.equals(mode));
        apply(mode);
    }

    /** Applies the stored mode — call once from the Application. */
    public static void apply(Context ctx) {
        apply(current(ctx));
    }

    private static void apply(String mode) {
        int night;
        if (DARK.equals(mode)) {
            night = AppCompatDelegate.MODE_NIGHT_YES;
        } else if (SYSTEM.equals(mode)) {
            night = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        } else {
            night = AppCompatDelegate.MODE_NIGHT_NO;
        }
        if (AppCompatDelegate.getDefaultNightMode() != night) {
            AppCompatDelegate.setDefaultNightMode(night);
        }
    }
}
