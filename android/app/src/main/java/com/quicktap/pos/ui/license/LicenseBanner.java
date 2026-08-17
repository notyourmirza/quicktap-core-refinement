package com.quicktap.pos.ui.license;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.quicktap.pos.R;
import com.quicktap.pos.license.LicenseService;
import com.quicktap.pos.license.LicenseState;
import com.quicktap.pos.util.AppPrefs;
import com.quicktap.pos.util.SupportContact;

/**
 * Reusable persistent banner: "VERIFY YOUR LICENSE" / "CONTACT".
 *
 * Drop {@code <com.quicktap.pos.ui.license.LicenseBanner/>} at the top of any
 * screen and call {@link #refresh()}. It hides itself only while the SERVER
 * reports an active, confirmed licence.
 */
public class LicenseBanner extends FrameLayout {

    private TextView textStatus;
    private MaterialButton buttonVerify;
    private MaterialButton buttonContact;

    public LicenseBanner(Context context) { super(context); init(); }

    public LicenseBanner(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LicenseBanner(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.view_license_banner, this, true);
        textStatus = findViewById(R.id.textBannerStatus);
        buttonVerify = findViewById(R.id.buttonBannerVerify);
        buttonContact = findViewById(R.id.buttonBannerContact);

        buttonVerify.setOnClickListener(v ->
                getContext().startActivity(new Intent(getContext(), VerifyLicenseActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        buttonContact.setOnClickListener(v -> SupportContact.chat(getContext(),
                "*License activation request*" + SupportContact.signature(getContext())));

        applyCached();
    }

    /** Paints from the cached hint first, then asks the server for the truth. */
    public void refresh() {
        applyCached();
        LicenseService.status(getContext(), this::apply);
    }

    public void apply(LicenseState state) {
        if (state == null) return;
        if (LicenseState.ACTIVE.equals(state.state) && state.unlocked && state.confirmed) {
            setVisibility(GONE);
            return;
        }
        // OFFLINE keeps the banner visible: an unreachable server is never a licence.
        setVisibility(VISIBLE);
        textStatus.setText(state.headline());
    }

    private void applyCached() {
        AppPrefs prefs = AppPrefs.get(getContext());
        boolean ok = LicenseState.ACTIVE.equals(prefs.getLicenseState()) && prefs.isLicenseConfirmed();
        setVisibility(ok ? GONE : VISIBLE);
        if (!ok) {
            LicenseState cached = new LicenseState();
            cached.state = prefs.getLicenseState();
            textStatus.setText(cached.headline());
        }
    }

    /** Convenience for screens that just want it wired on resume. */
    public static void refresh(@Nullable View banner) {
        if (banner instanceof LicenseBanner) ((LicenseBanner) banner).refresh();
    }
}
