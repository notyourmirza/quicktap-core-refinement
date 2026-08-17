package com.quicktap.pos.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.quicktap.pos.auth.DeviceIdentity;
import com.quicktap.pos.auth.SessionManager;
import com.quicktap.pos.databinding.ActivityLoginBinding;
import com.quicktap.pos.license.LicenseGate;
import com.quicktap.pos.ui.license.RegisterActivity;

/**
 * Username + password sign-in against the QuickTap REST API.
 *
 * On success the server binds this device to the account (device binding) and
 * returns a JWT access/refresh pair which {@link SessionManager} stores.
 */
public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private SessionManager session;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        session = SessionManager.get(this);

        binding.textDevice.setText("Device: " + DeviceIdentity.name());
        binding.layoutEmail.setHint("Username");
        binding.buttonLogin.setOnClickListener(v -> submit());
        binding.buttonRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
        binding.buttonPlans.setOnClickListener(v -> startActivity(
                new android.content.Intent(this, com.quicktap.pos.ui.plans.PlansActivity.class)));
        binding.buttonContact.setOnClickListener(v -> contactAdmin());
        binding.textForgot.setOnClickListener(v -> contactAdmin());
    }

    private void submit() {
        String username = value(binding.inputEmail.getText());
        String password = value(binding.inputPassword.getText());

        if (TextUtils.isEmpty(username)) {
            binding.layoutEmail.setError("Enter your username");
            return;
        }
        binding.layoutEmail.setError(null);
        if (password.length() < 4) {
            binding.layoutPassword.setError("Enter your password");
            return;
        }
        binding.layoutPassword.setError(null);

        setBusy(true);
        session.login(username, password, (ok, message, code) -> {
            setBusy(false);
            if (ok) {
                session.markUnlocked();
                // Never open the app straight away: the server-authoritative
                // licence gate decides where this account belongs.
                setBusy(true);
                LicenseGate.resolveAndRoute(this);
                return;
            }
            // Blocked device / suspended shop: surface the server's reason inline.
            showStatus(message == null || message.isEmpty()
                    ? "Sign in failed. Please try again." : message);

        });
    }

    /** Plan catalogue is owned by the Super Admin panel; this is the read-only view. */
    private void showPlans() {
        startActivity(new Intent(this, com.quicktap.pos.ui.plans.PlansActivity.class));
    }

    private void contactAdmin() {
        com.quicktap.pos.ui.support.ContactSheet.show(this, "Licence / approval");
    }


    private void showStatus(String message) {
        binding.textStatus.setText(message);
        binding.textStatus.setVisibility(View.VISIBLE);
    }

    private void setBusy(boolean busy) {
        binding.progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        binding.buttonLogin.setEnabled(!busy);
    }

    private String value(CharSequence text) { return text == null ? "" : text.toString().trim(); }
}
