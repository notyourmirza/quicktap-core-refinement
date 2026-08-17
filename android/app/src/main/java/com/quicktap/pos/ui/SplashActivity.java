package com.quicktap.pos.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.quicktap.pos.R;
import com.quicktap.pos.auth.SessionManager;
import com.quicktap.pos.license.LicenseGate;
import com.quicktap.pos.license.LicenseService;
import com.quicktap.pos.net.RemoteEndpoint;
import com.quicktap.pos.sync.SyncEngine;
import com.quicktap.pos.theme.RemoteTheme;
import com.quicktap.pos.util.AppPrefs;

/**
 * Remote-controlled, animated splash screen. Every visual detail is driven
 * by the Super Admin's cached splash configuration (see {@link AppPrefs})
 * so it renders correctly offline, then routes exactly as before.
 */
public class SplashActivity extends AppCompatActivity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable routeRunnable;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppPrefs prefs = AppPrefs.get(this);
        AppCompatDelegate.setDefaultNightMode(prefs.isDarkMode()
                ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);

        setContentView(R.layout.activity_splash);
        renderAndAnimate(prefs);

        // The endpoint (Firebase controlled) must be resolved before the licence
        // gate asks the server anything. Application#onCreate already primed it;
        // this refresh keeps a changed endpoint / support number up to date.
        RemoteEndpoint.init(getApplicationContext());
        RemoteEndpoint.fetchAsync(getApplicationContext());
        LicenseService.syncAppConfig(getApplicationContext());

        SessionManager session = SessionManager.get(this);
        int duration = Math.max(600, prefs.getSplashDurationMs());

        routeRunnable = () -> {
            if (isFinishing()) return;
            if (!session.isSignedIn()) {
                // No account / no session at all -> sign in or create an account.
                go(LoginActivity.class);
                return;
            }
            if (session.shouldLock()) {
                go(UnlockActivity.class);
                return;
            }
            session.markActivity();

            // Quietly catch up with the server in the background…
            RemoteTheme.refresh(getApplicationContext(), null);
            SyncEngine.syncAuto(getApplicationContext());

            // …and let the single licence gate decide the destination. Every
            // state (pending / expired / revoked / suspended / blocked /
            // unconfirmed / active) is routed there, never here.
            LicenseGate.resolveAndRoute(SplashActivity.this);
        };
        handler.postDelayed(routeRunnable, duration);
    }

    private void renderAndAnimate(AppPrefs prefs) {
        View root = findViewById(R.id.splashRoot);
        FrameLayout logoWrap = findViewById(R.id.splashLogoWrap);
        ImageView logo = findViewById(R.id.splashLogo);
        TextView title = findViewById(R.id.splashTitle);
        TextView tagline = findViewById(R.id.splashTagline);
        ProgressBar progress = findViewById(R.id.splashProgress);
        LinearLayout creditGroup = findViewById(R.id.splashCreditGroup);
        TextView creditPrefix = findViewById(R.id.splashCreditPrefix);
        TextView creditText = findViewById(R.id.splashCreditText);

        int background = parse(prefs.getSplashBackgroundColor(), 0);
        int textColor = parse(prefs.getSplashTextColor(), 0);
        int accent = parse(prefs.getSplashAccentColor(), 0);

        if (background != 0) root.setBackgroundColor(background);
        if (accent != 0) {
            GradientDrawable badge = new GradientDrawable();
            badge.setShape(GradientDrawable.OVAL);
            badge.setColor(accent);
            logoWrap.setBackground(badge);
            progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(accent));
            creditText.setTextColor(accent);
        }
        if (textColor != 0) {
            title.setTextColor(textColor);
            tagline.setTextColor(textColor);
        }

        title.setText(prefs.getSplashTitle());
        tagline.setText(prefs.getSplashTagline());

        boolean showProgress = prefs.isSplashShowProgress();
        progress.setVisibility(showProgress ? View.VISIBLE : View.GONE);

        boolean showCredit = prefs.isSplashShowCredit();
        creditGroup.setVisibility(showCredit ? View.VISIBLE : View.GONE);
        creditPrefix.setText(prefs.getSplashCreditPrefix());
        creditText.setText(prefs.getSplashCreditText());

        animateLogo(logoWrap, prefs.getSplashAnimation());

        title.animate().alpha(1f).translationY(0f).setStartDelay(150)
                .setDuration(360).setInterpolator(new AccelerateDecelerateInterpolator()).start();
        title.setTranslationY(24f);
        tagline.setTranslationY(24f);
        tagline.animate().alpha(1f).translationY(0f).setStartDelay(240)
                .setDuration(360).setInterpolator(new AccelerateDecelerateInterpolator()).start();
        if (showProgress) {
            progress.animate().alpha(1f).setStartDelay(420).setDuration(300).start();
        }
        if (showCredit) {
            creditGroup.setTranslationY(16f);
            creditGroup.animate().alpha(1f).translationY(0f).setStartDelay(500)
                    .setDuration(360).start();
        }
    }

    /** Applies the entrance animation named by the Super Admin's config. */
    private void animateLogo(View logo, String animation) {
        logo.setAlpha(0f);
        switch (animation == null ? "fade" : animation) {
            case "zoom":
                logo.setScaleX(0.4f);
                logo.setScaleY(0.4f);
                logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(500)
                        .setInterpolator(new OvershootInterpolator()).start();
                break;
            case "slide_up":
                logo.setTranslationY(80f);
                logo.animate().alpha(1f).translationY(0f).setDuration(450)
                        .setInterpolator(new AccelerateDecelerateInterpolator()).start();
                break;
            case "pulse":
                logo.setScaleX(0.85f);
                logo.setScaleY(0.85f);
                logo.animate().alpha(1f).setDuration(300).withEndAction(() -> pulse(logo)).start();
                break;
            case "rotate":
                logo.setRotation(-90f);
                logo.setScaleX(0.6f);
                logo.setScaleY(0.6f);
                logo.animate().alpha(1f).rotation(0f).scaleX(1f).scaleY(1f).setDuration(520)
                        .setInterpolator(new OvershootInterpolator()).start();
                break;
            case "fade":
            default:
                logo.setScaleX(0.85f);
                logo.setScaleY(0.85f);
                logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(450).start();
                break;
        }
    }

    /** Subtle continuous breathing effect for the "pulse" style. */
    private void pulse(View view) {
        ValueAnimator animator = ValueAnimator.ofFloat(1f, 1.08f, 1f);
        animator.setDuration(1100);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(a -> {
            float scale = (float) a.getAnimatedValue();
            view.setScaleX(scale);
            view.setScaleY(scale);
        });
        animator.start();
    }

    private int parse(String hex, int fallback) {
        try {
            return (hex == null || hex.trim().isEmpty()) ? fallback : Color.parseColor(hex);
        } catch (Exception e) {
            return fallback;
        }
    }

    private void go(Class<?> target) {
        if (isFinishing()) return;
        startActivity(new Intent(this, target));
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (routeRunnable != null) handler.removeCallbacks(routeRunnable);
    }
}
