package com.quicktap.pos.ui.license;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.quicktap.pos.R;
import com.quicktap.pos.license.LicenseState;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * "Congratulations" popup shown after the SERVER confirms a verification.
 * Every line of text comes from the server payload — no hard-coded durations.
 */
public final class LicenseSuccessDialog {

    public interface OnContinue { void onContinue(); }

    private LicenseSuccessDialog() { }

    public static void show(Activity activity, LicenseState state, OnContinue onContinue) {
        if (activity == null || activity.isFinishing()) return;

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_license_success);
        dialog.setCancelable(false);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView duration = dialog.findViewById(R.id.textDuration);
        TextView expiry = dialog.findViewById(R.id.textExpiry);
        MaterialButton button = dialog.findViewById(R.id.buttonContinue);

        duration.setText(state.durationLine());
        if (state.expiresAtMs > 0) {
            expiry.setVisibility(View.VISIBLE);
            expiry.setText("Valid until " + new SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    .format(new Date(state.expiresAtMs))
                    + (state.daysLeft >= 0 ? "  ·  " + state.daysLeft + " days left" : ""));
        } else {
            expiry.setVisibility(View.GONE);
        }

        button.setOnClickListener(v -> {
            dialog.dismiss();
            if (onContinue != null) onContinue.onContinue();
        });
        dialog.show();
    }
}
