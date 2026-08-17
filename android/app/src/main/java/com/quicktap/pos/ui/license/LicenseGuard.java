package com.quicktap.pos.ui.license;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.quicktap.pos.license.LicenseGate;
import com.quicktap.pos.license.LicenseService;
import com.quicktap.pos.license.LicenseState;

/**
 * One reusable mechanism for every protected screen.
 *
 * Instead of copying the banner into dozens of layouts, a protected Activity
 * calls {@link #protect(Activity)} from {@code onResume()}. It:
 *   1. shows the shared {@link LicenseBanner} at the very top of the existing
 *      content view (nothing is covered — the content is pushed down), and
 *   2. asks the SERVER for the current state and hands the answer to the single
 *      {@link LicenseGate} so a pending / expired / revoked / suspended /
 *      blocked / unconfirmed account is routed away from protected screens.
 *
 * Nothing here grants access: the gate keeps the offline grace rules and the
 * server keeps rejecting protected API calls regardless of the UI.
 */
public final class LicenseGuard {

    private LicenseGuard() { }

    /** Attaches (once) the banner and re-validates the account with the server. */
    public static void protect(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        final LicenseBanner banner = attach(activity);
        LicenseService.status(activity, state -> {
            if (activity.isFinishing()) return;
            if (banner != null) banner.apply(state);
            if (isAllowed(activity, state)) return;
            LicenseGate.route(activity, state);
        });
    }

    /** True when this state may keep using an already-open protected screen. */
    private static boolean isAllowed(Activity activity, LicenseState state) {
        if (state == null) return false;
        if (LicenseState.OFFLINE.equals(state.state)) {
            // Only a previously server-confirmed ACTIVE licence rides out an outage.
            return LicenseGate.offlineGraceAllows(activity);
        }
        return LicenseState.ACTIVE.equals(state.state) && state.unlocked && state.confirmed;
    }

    /** Inserts the banner at the top of the activity's content, at most once. */
    @Nullable
    public static LicenseBanner attach(Activity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return null;

        View root = content.getChildAt(0);
        if (root instanceof LinearLayout && "license_guard".equals(root.getTag())) {
            return (LicenseBanner) ((LinearLayout) root).getChildAt(0);
        }

        ViewGroup.LayoutParams params = root.getLayoutParams();
        content.removeView(root);

        LinearLayout wrapper = new LinearLayout(activity);
        wrapper.setTag("license_guard");
        wrapper.setOrientation(LinearLayout.VERTICAL);

        LicenseBanner banner = new LicenseBanner(activity);
        banner.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrapper.addView(banner);

        wrapper.addView(root, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        content.addView(wrapper, params == null
                ? new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT)
                : params);
        return banner;
    }
}
