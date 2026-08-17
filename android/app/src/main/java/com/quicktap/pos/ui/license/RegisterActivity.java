package com.quicktap.pos.ui.license;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.quicktap.pos.auth.DeviceIdentity;
import com.quicktap.pos.databinding.ActivityRegisterBinding;
import com.quicktap.pos.license.LicenseService;
import com.quicktap.pos.license.LicenseState;
import com.quicktap.pos.util.SupportContact;

/**
 * Account creation. Local validation only catches obvious typing errors — the
 * account, the device binding and the licence request are all created by the
 * server (POST /v1/auth/register), which stays the single authority.
 */
public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private boolean busy;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.textDevice.setText("Device: " + DeviceIdentity.name());
        binding.buttonCreate.setOnClickListener(v -> submit());
        binding.buttonHaveAccount.setOnClickListener(v -> finish());
        binding.buttonContact.setOnClickListener(v ->
                SupportContact.chat(this, "Hello, I need help creating my account."
                        + SupportContact.signature(this)));
    }

    // ------------------------------------------------------------ submit

    private void submit() {
        if (busy) return;

        String shop = text(binding.inputShop.getText());
        String username = text(binding.inputUsername.getText());
        String password = raw(binding.inputPassword.getText());
        String confirm = raw(binding.inputConfirm.getText());
        String owner = text(binding.inputOwner.getText());
        String phone = text(binding.inputPhone.getText());

        clearErrors();
        if (TextUtils.isEmpty(shop)) {
            binding.layoutShop.setError("Enter your shop name");
            return;
        }
        if (!username.matches("[a-zA-Z0-9._-]{3,80}")) {
            binding.layoutUsername.setError("3+ characters: letters, numbers, . _ -");
            return;
        }
        if (password.length() < 6) {
            binding.layoutPassword.setError("Use at least 6 characters");
            return;
        }
        if (!password.equals(confirm)) {
            binding.layoutConfirm.setError("Passwords do not match");
            return;
        }

        setBusy(true);
        hideStatus();
        LicenseService.register(this, shop, username, password, owner, phone, state -> {
            setBusy(false);
            if (state.success) {
                showCreated();
                return;
            }
            if (LicenseState.OFFLINE.equals(state.state)) {
                showStatus("No connection to the server. Please try again.");
                return;
            }
            if ("DEVICE_ALREADY_REGISTERED".equals(state.code)) {
                showStatus("This device is already registered with an account.");
                return;
            }
            if ("USERNAME_TAKEN".equals(state.code)) {
                binding.layoutUsername.setError("That username is already taken");
                return;
            }
            showStatus(state.describe());
        });
    }

    /** Registration never opens the app — it always ends on the pending screen. */
    private void showCreated() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Account Created Successfully")
                .setMessage("Your license activation is pending.")
                .setCancelable(false)
                .setPositiveButton("Continue", (d, w) -> {
                    startActivity(LicenseStatusActivity.intent(this, LicenseState.PENDING));
                    finish();
                })
                .show();
    }

    // ------------------------------------------------------------- state

    private void setBusy(boolean value) {
        busy = value;
        binding.progress.setVisibility(value ? View.VISIBLE : View.GONE);
        binding.buttonCreate.setEnabled(!value);
        binding.buttonCreate.setText(value ? "Creating account…" : "Create Account");
        binding.buttonHaveAccount.setEnabled(!value);
    }

    private void showStatus(String message) {
        binding.textStatus.setText(message);
        binding.textStatus.setVisibility(View.VISIBLE);
    }

    private void hideStatus() { binding.textStatus.setVisibility(View.GONE); }

    private void clearErrors() {
        binding.layoutShop.setError(null);
        binding.layoutUsername.setError(null);
        binding.layoutPassword.setError(null);
        binding.layoutConfirm.setError(null);
    }

    private String text(CharSequence value) { return value == null ? "" : value.toString().trim(); }

    /** Passwords are read as typed and never trimmed, stored or logged. */
    private String raw(CharSequence value) { return value == null ? "" : value.toString(); }
}
