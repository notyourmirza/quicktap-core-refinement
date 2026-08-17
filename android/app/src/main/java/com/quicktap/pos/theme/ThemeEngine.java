package com.quicktap.pos.theme;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowInsetsControllerCompat;

import com.quicktap.pos.R;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Applies the Super Admin brand colour to every screen at runtime.
 *
 * <p>Android themes are frozen at inflation time, so a colour that arrives from
 * the server can never reach a layout that hardcodes {@code @color/brand_*}.
 * This engine walks the view tree after each screen is drawn and repaints
 * anything that is brand coloured — buttons, chips, the bottom bar, inputs,
 * pills, icons and text — with the colour the admin panel published.
 *
 * <p>Installed once from {@code QuickTapApp}; no Activity needs to opt in.
 */
public final class ThemeEngine {

    /** Palette colours that are considered "brand" and therefore repaintable. */
    private static final int[] BRAND_SEEDS = {
            Color.parseColor("#2F5BFF"), // brand_primary   (design default)
            Color.parseColor("#1E3FCB"), // brand_primary_dark
            Color.parseColor("#5B7CFF"), // brand_secondary
            Color.parseColor("#0E9F6E"), // legacy emerald primary
            Color.parseColor("#047857"), // legacy emerald dark
            Color.parseColor("#34D399"), // legacy emerald secondary
    };


    private ThemeEngine() { }

    /* ------------------------------------------------------------------ */
    /* Install                                                             */
    /* ------------------------------------------------------------------ */

    public static void install(Application app) {
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(@NonNull Activity a, @Nullable Bundle b) { }

            @Override public void onActivityStarted(@NonNull Activity a) { }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                attach(activity);
            }

            @Override public void onActivityPaused(@NonNull Activity a) { }
            @Override public void onActivityStopped(@NonNull Activity a) { }
            @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) { }
            @Override public void onActivityDestroyed(@NonNull Activity a) { }
        });
    }

    /**
     * Repaints the activity now and keeps repainting as fragments, list rows and
     * dialogs are added to the window.
     *
     * <p>Painting is generation stamped: a view is only visited once per theme
     * revision, so scrolling a long list never re-walks views that are already
     * correct. That keeps the UI at 60fps instead of repainting every frame.
     */
    public static void attach(Activity activity) {
        View root = activity.findViewById(android.R.id.content);
        if (root == null) return;

        applySystemBars(activity);
        apply(root);

        if (root.getTag(R.id.tag_theme_root) != null) return;
        root.setTag(R.id.tag_theme_root, Boolean.TRUE);
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> schedule(root));
    }

    /** Coalesces layout-pass repaints into one cheap pass per frame batch. */
    private static void schedule(View root) {
        if (pendingRoot != null) return;
        pendingRoot = root;
        root.postDelayed(() -> {
            View target = pendingRoot;
            pendingRoot = null;
            if (target != null && target.isAttachedToWindow()) apply(target);
        }, 120L);
    }

    /** Drops every paint stamp so the next pass repaints with new admin colours. */
    public static void invalidate() {
        generation++;
    }

    /** Recursively repaints a view tree with the current design language. */
    public static void apply(View view) {
        if (view == null) return;
        Context ctx = view.getContext();
        cachePreset(ctx);
        int accent = RemoteTheme.primary(ctx);
        int accentAlt = RemoteTheme.secondary(ctx);
        applyTo(view, accent, accentAlt);
    }

    private static void applyTo(View view, int accent, int accentAlt) {
        Object stamp = view.getTag(R.id.tag_theme_stamp);
        boolean painted = stamp instanceof Integer && (Integer) stamp == generation;
        if (!painted) {
            view.setTag(R.id.tag_theme_stamp, generation);
            paint(view, accent, accentAlt);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyTo(group.getChildAt(i), accent, accentAlt);
            }
        }
    }

    private static int generation = 1;
    private static View pendingRoot;




    /** Reads the assigned preset once per pass instead of once per view. */
    private static void cachePreset(Context ctx) {
        preset = RemoteTheme.preset(ctx);
        cBackground = parse(preset.background);
        cSurface = parse(preset.surface);
        cSurfaceMuted = parse(preset.surfaceMuted);
        cOutline = parse(preset.outline);
        cTextPrimary = parse(preset.textPrimary);
        cTextMuted = parse(preset.textMuted);
        cHeroStart = parse(preset.heroStart);
        cHeroEnd = parse(preset.heroEnd);
        density = ctx.getResources().getDisplayMetrics().density;
    }

    private static ThemePresets.Preset preset = ThemePresets.byKey(null);
    private static int cBackground = Color.WHITE;
    private static int cSurface = Color.WHITE;
    private static int cSurfaceMuted = Color.parseColor("#F4F5F7");
    private static int cOutline = Color.parseColor("#E5E7EC");
    private static int cTextPrimary = Color.parseColor("#0B0F19");
    private static int cTextMuted = Color.parseColor("#6B7280");
    private static int cHeroStart = Color.parseColor("#0B0F19");
    private static int cHeroEnd = Color.parseColor("#0B0F19");
    private static float density = 3f;

    private static int parse(String hex) {
        try { return Color.parseColor(hex); } catch (Exception e) { return Color.WHITE; }
    }

    private static int dp(int value) { return Math.round(value * density); }

    /** Design-language seeds baked into the XML layouts, mapped to the preset. */
    private static final int SEED_BACKGROUND = Color.parseColor("#FBFBFC");
    private static final int SEED_SURFACE = Color.parseColor("#FFFFFF");
    private static final int SEED_SURFACE_MUTED = Color.parseColor("#F4F5F7");
    private static final int SEED_OUTLINE = Color.parseColor("#E5E7EC");
    private static final int SEED_OUTLINE_SOFT = Color.parseColor("#EFF0F3");
    private static final int SEED_INK = Color.parseColor("#0B0F19");
    private static final int SEED_MUTED = Color.parseColor("#6B7280");

    private static boolean sameRgb(int a, int b) {
        return (a & 0x00FFFFFF) == (b & 0x00FFFFFF);
    }

    /**
     * Repaints the neutral layer — page background, cards, dividers, ink and
     * secondary text — so a preset changes the whole look, not just the accent.
     */
    private static void paintSurfaces(View view) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            int color = text.getCurrentTextColor();
            if (sameRgb(color, SEED_INK)) text.setTextColor(cTextPrimary);
            else if (sameRgb(color, SEED_MUTED)) text.setTextColor(cTextMuted);
            if (text.getHintTextColors() != null
                    && sameRgb(text.getHintTextColors().getDefaultColor(), SEED_MUTED)) {
                text.setHintTextColor(cTextMuted);
            }
        }
        if (view instanceof ImageView) {
            ImageView image = (ImageView) view;
            ColorStateList tint = image.getImageTintList();
            if (tint != null) {
                int color = tint.getDefaultColor();
                if (sameRgb(color, SEED_INK)) image.setImageTintList(ColorStateList.valueOf(cTextPrimary));
                else if (sameRgb(color, SEED_MUTED)) image.setImageTintList(ColorStateList.valueOf(cTextMuted));
            }
        }

        int background = backgroundColor(view.getBackground());
        if (background != 0 && Color.alpha(background) >= 0xF0) {
            if (sameRgb(background, SEED_BACKGROUND)) paintBackground(view, cBackground);
            else if (sameRgb(background, SEED_SURFACE)) paintBackground(view, cSurface);
            else if (sameRgb(background, SEED_SURFACE_MUTED)) paintBackground(view, cSurfaceMuted);
            else if (sameRgb(background, SEED_OUTLINE) || sameRgb(background, SEED_OUTLINE_SOFT)) {
                paintBackground(view, cOutline);
            } else if (sameRgb(background, SEED_INK)) {
                paintGradient(view, cHeroStart, cHeroEnd);
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* Per widget painting                                                 */
    /* ------------------------------------------------------------------ */

    private static void paint(View view, int accent, @SuppressWarnings("unused") int accentAlt) {
        String tag = view.getTag() instanceof String ? (String) view.getTag() : "";
        int onAccent = RemoteTheme.onColor(accent);
        int soft = withAlpha(accent, 0x1F);
        int softer = withAlpha(accent, 0x14);

        paintSurfaces(view);


        // --- explicit opt-in tags -------------------------------------------------
        if (tag.contains("brand_bg")) {
            paintBackground(view, accent);
            if (view instanceof TextView) ((TextView) view).setTextColor(onAccent);
        } else if (tag.contains("brand_soft")) {
            paintBackground(view, soft);
        } else if (tag.contains("brand_text") && view instanceof TextView) {
            ((TextView) view).setTextColor(accent);
        } else if (tag.contains("brand_icon") && view instanceof ImageView) {
            ((ImageView) view).setImageTintList(ColorStateList.valueOf(accent));
        }

        // --- material widgets -----------------------------------------------------
        if (view instanceof MaterialButton) {
            MaterialButton button = (MaterialButton) view;
            boolean outlined = button.getStrokeWidth() > 0;
            // Button shape follows the design language.
            button.setCornerRadius(dp(Math.min(preset.cornerRadiusDp, 20)));
            if (outlined) {
                if (tag.contains("brand")) {
                    button.setStrokeColor(ColorStateList.valueOf(accent));
                    button.setTextColor(accent);
                    button.setIconTint(ColorStateList.valueOf(accent));
                    button.setRippleColor(ColorStateList.valueOf(softer));
                } else {
                    button.setStrokeColor(ColorStateList.valueOf(cOutline));
                    button.setTextColor(cTextPrimary);
                }
            } else if (isBrand(defaultColor(button.getBackgroundTintList()))) {
                button.setBackgroundTintList(ColorStateList.valueOf(accent));
                button.setTextColor(onAccent);
                button.setIconTint(ColorStateList.valueOf(onAccent));
            }
            return;
        }


        if (view instanceof FloatingActionButton) {
            FloatingActionButton fab = (FloatingActionButton) view;
            fab.setBackgroundTintList(ColorStateList.valueOf(accent));
            fab.setImageTintList(ColorStateList.valueOf(onAccent));
            return;
        }

        if (view instanceof Chip) {
            Chip chip = (Chip) view;
            // Always derive the unchecked state from the theme, never from the
            // chip's current colour — re-painting a checked chip would otherwise
            // bake white text into the unchecked state and make it invisible.
            chip.setChipBackgroundColor(checkable(accent, cSurface));
            chip.setTextColor(checkable(onAccent, cTextPrimary));
            chip.setChipStrokeColor(checkable(accent, cOutline));
            chip.setRippleColor(ColorStateList.valueOf(softer));
            return;
        }

        if (view instanceof BottomNavigationView) {
            BottomNavigationView nav = (BottomNavigationView) view;
            int muted = nav.getItemTextColor() != null
                    ? nav.getItemTextColor().getColorForState(new int[0], Color.GRAY)
                    : Color.GRAY;
            nav.setItemIconTintList(checkable(accent, muted));
            nav.setItemTextColor(checkable(accent, muted));
            nav.setItemActiveIndicatorColor(ColorStateList.valueOf(soft));
            nav.setItemRippleColor(ColorStateList.valueOf(softer));
            return;
        }

        if (view instanceof com.google.android.material.navigation.NavigationView) {
            com.google.android.material.navigation.NavigationView nav =
                    (com.google.android.material.navigation.NavigationView) view;
            nav.setBackgroundColor(cSurface);
            nav.setItemIconTintList(checkable(accent, cTextMuted));
            nav.setItemTextColor(checkable(accent, cTextPrimary));
            nav.setItemBackground(null);
            try { nav.setItemBackgroundResource(0); } catch (Throwable ignored) {}
            return;
        }


        if (view instanceof TextInputLayout) {
            TextInputLayout input = (TextInputLayout) view;
            input.setBoxStrokeColorStateList(focusable(accent,
                    input.getBoxStrokeColor() == 0 ? accent : input.getBoxStrokeColor()));
            input.setHintTextColor(ColorStateList.valueOf(accent));
            input.setCursorColor(ColorStateList.valueOf(accent));
            return;
        }

        if (view instanceof CircularProgressIndicator) {
            ((CircularProgressIndicator) view).setIndicatorColor(accent);
            ((CircularProgressIndicator) view).setTrackColor(soft);
            return;
        }

        if (view instanceof LinearProgressIndicator) {
            ((LinearProgressIndicator) view).setIndicatorColor(accent);
            ((LinearProgressIndicator) view).setTrackColor(soft);
            return;
        }

        if (view instanceof ProgressBar) {
            ((ProgressBar) view).setIndeterminateTintList(ColorStateList.valueOf(accent));
            return;
        }

        if (view instanceof MaterialCardView) {
            MaterialCardView card = (MaterialCardView) view;
            int fill = card.getCardBackgroundColor().getDefaultColor();
            if (isBrand(fill)) {
                card.setCardBackgroundColor(accent);
            } else if (sameRgb(fill, SEED_SURFACE)) {
                card.setCardBackgroundColor(cSurface);
            } else if (sameRgb(fill, SEED_SURFACE_MUTED) || sameRgb(fill, SEED_BACKGROUND)) {
                card.setCardBackgroundColor(cSurfaceMuted);
            }
            // Radius + elevation are part of the design language, not the brand.
            card.setRadius(dp(preset.cornerRadiusDp));
            card.setCardElevation(dp(preset.elevationDp));
            if (card.getStrokeWidth() > 0 || preset.strokeWidthDp > 0) {
                card.setStrokeWidth(dp(preset.strokeWidthDp));
                card.setStrokeColor(isBrand(card.getStrokeColor()) ? accent : cOutline);
            }
            card.setRippleColor(ColorStateList.valueOf(softer));
            return;
        }


        // --- catch-all: anything still painted with an old brand colour -----------
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            if (isBrand(text.getCurrentTextColor())) text.setTextColor(accent);
        }

        if (view instanceof ImageView) {
            ImageView image = (ImageView) view;
            ColorStateList tint = image.getImageTintList();
            if (tint != null && isBrand(tint.getDefaultColor())) {
                image.setImageTintList(ColorStateList.valueOf(accent));
            }
        }

        int background = backgroundColor(view.getBackground());
        if (background != 0) {
            if (isBrand(background)) {
                paintBackground(view, accent);
            } else if (isBrandTinted(background)) {
                paintBackground(view, withAlpha(accent, Color.alpha(background)));
            }
        }

    }

    /* ------------------------------------------------------------------ */
    /* System bars                                                         */
    /* ------------------------------------------------------------------ */

    private static void applySystemBars(Activity activity) {
        Window window = activity.getWindow();
        if (window == null) return;
        View decor = window.getDecorView();
        cachePreset(activity);
        window.setStatusBarColor(cBackground);
        window.setNavigationBarColor(cBackground);
        if (decor.getRootView() != null) decor.setBackgroundColor(cBackground);
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(window, decor);
        // Dark presets need light icons; light presets need dark icons.
        controller.setAppearanceLightStatusBars(!preset.dark);
        controller.setAppearanceLightNavigationBars(!preset.dark);
    }

    /* ------------------------------------------------------------------ */
    /* Helpers                                                             */
    /* ------------------------------------------------------------------ */

    /** Repaints a hero panel with the preset's two-stop gradient. */
    private static void paintGradient(View view, int start, int end) {
        Drawable background = view.getBackground();
        if (background instanceof GradientDrawable) {
            GradientDrawable shape = (GradientDrawable) background.mutate();
            shape.setColors(new int[]{ start, end });
        } else {
            GradientDrawable shape = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR, new int[]{ start, end });
            shape.setCornerRadius(dp(preset.cornerRadiusDp));
            view.setBackground(shape);
        }
    }

    private static void paintBackground(View view, int color) {
        Drawable background = view.getBackground();
        if (background instanceof GradientDrawable) {
            GradientDrawable shape = (GradientDrawable) background.mutate();

            shape.setColors(null);
            shape.setColor(color);
        } else if (background instanceof ColorDrawable) {
            ((ColorDrawable) background.mutate()).setColor(color);
        } else if (background instanceof LayerDrawable) {
            LayerDrawable layers = (LayerDrawable) background.mutate();
            for (int i = 0; i < layers.getNumberOfLayers(); i++) {
                Drawable layer = layers.getDrawable(i);
                if (layer instanceof GradientDrawable) {
                    ((GradientDrawable) layer).setColors(null);
                    ((GradientDrawable) layer).setColor(color);
                }
            }
        } else {
            view.setBackgroundColor(color);
        }
    }

    private static int backgroundColor(Drawable drawable) {
        if (drawable instanceof ColorDrawable) return ((ColorDrawable) drawable).getColor();
        if (drawable instanceof GradientDrawable && Build.VERSION.SDK_INT >= 24) {
            ColorStateList colors = ((GradientDrawable) drawable).getColor();
            if (colors != null) return colors.getDefaultColor();
        }
        return 0;
    }

    private static int defaultColor(ColorStateList list) {
        return list == null ? 0 : list.getDefaultColor();
    }

    /** True when the colour is one of the brand seeds (opaque match). */
    private static boolean isBrand(int color) {
        if (Color.alpha(color) < 0xF0) return false;
        for (int seed : BRAND_SEEDS) {
            if ((color & 0x00FFFFFF) == (seed & 0x00FFFFFF)) return true;
        }
        return false;
    }

    /** True for translucent brand washes such as {@code #1A0E9F6E}. */
    private static boolean isBrandTinted(int color) {
        int alpha = Color.alpha(color);
        if (alpha == 0 || alpha >= 0xF0) return false;
        for (int seed : BRAND_SEEDS) {
            if ((color & 0x00FFFFFF) == (seed & 0x00FFFFFF)) return true;
        }
        return false;
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static ColorStateList checkable(int checked, int unchecked) {
        return new ColorStateList(
                new int[][]{ new int[]{ android.R.attr.state_checked }, new int[0] },
                new int[]{ checked, unchecked });
    }

    private static ColorStateList focusable(int focused, int idle) {
        return new ColorStateList(
                new int[][]{ new int[]{ android.R.attr.state_focused }, new int[0] },
                new int[]{ focused, idle });
    }
}
