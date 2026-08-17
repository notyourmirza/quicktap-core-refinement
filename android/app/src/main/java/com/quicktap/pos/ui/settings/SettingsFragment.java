package com.quicktap.pos.ui.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.quicktap.pos.auth.BiometricGate;
import com.quicktap.pos.auth.DeviceIdentity;
import com.quicktap.pos.auth.SessionManager;
import com.quicktap.pos.backup.BackupWorker;
import com.quicktap.pos.backup.DriveBackupManager;
import com.quicktap.pos.databinding.FragmentSettingsBinding;
import com.quicktap.pos.sync.SyncEngine;
import com.quicktap.pos.sync.SyncWorker;
import com.quicktap.pos.ui.LoginActivity;
import com.quicktap.pos.util.AppExecutors;
import com.quicktap.pos.util.AppPrefs;
import com.quicktap.pos.util.BackupUtil;
import com.quicktap.pos.util.DateUtil;

/**
 * Store profile, tax/discount, theme, printer, security (fingerprint + auto
 * lock), cloud sync, Google Drive backup and sign-out.
 */
public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private AppPrefs prefs;
    private SessionManager session;
    private ActivityResultLauncher<String> backupPicker;
    private ActivityResultLauncher<String[]> restorePicker;
    private ActivityResultLauncher<Intent> driveSignIn;
    private ActivityResultLauncher<String> logoPicker;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        prefs = AppPrefs.get(requireContext());
        session = SessionManager.get(requireContext());

        backupPicker = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/octet-stream"),
                this::doBackup);
        restorePicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), this::doRestore);
        driveSignIn = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> refreshCloudStatus());

        binding.inputStoreName.setText(prefs.getStoreName());
        binding.inputPhone.setText(prefs.getStorePhone());
        binding.inputAddress.setText(prefs.getStoreAddress());
        binding.inputCurrency.setText(prefs.getCurrency());
        binding.inputFooter.setText(prefs.getReceiptFooter());
        binding.inputInvoicePrefix.setText(prefs.getInvoicePrefix());
        binding.inputTax.setText(String.valueOf(prefs.getTaxPercent()));
        binding.inputDiscount.setText(String.valueOf(prefs.getDiscountPercent()));
        binding.switchDark.setChecked(prefs.isDarkMode());

        binding.buttonSave.setOnClickListener(v -> save());
        binding.switchDark.setOnCheckedChangeListener((v, checked) -> {
            prefs.setDarkMode(checked);
            AppCompatDelegate.setDefaultNightMode(checked
                    ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        });
        binding.buttonPrinter.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), PrinterSettingsActivity.class)));
        binding.buttonBackup.setOnClickListener(v ->
                backupPicker.launch(BackupUtil.suggestedFileName()));
        binding.buttonRestore.setOnClickListener(v ->
                restorePicker.launch(new String[]{"*/*"}));
        binding.buttonSignOut.setOnClickListener(v -> signOut());

        setUpAppName();
        setUpReceiptDesign();
        setUpSecurity();
        setUpCloud();
    }

    // ------------------------------------------------------------- app name

    /**
     * The app name lives on the server (themes.app_name) so every device of the
     * shop follows it. We show the cached value instantly, refresh it from the
     * API, and push edits back through the existing endpoint.
     */
    private void setUpAppName() {
        binding.inputAppName.setText(prefs.getThemeAppName());
        binding.textAppNameStatus.setText("Current: " + prefs.getThemeAppName());
        binding.buttonSaveAppName.setOnClickListener(v -> saveAppName());
        fetchAppName();
    }

    /** Pulls the live value so the field never shows a stale name. */
    private void fetchAppName() {
        AppExecutors.io().execute(() -> {
            boolean changed = com.quicktap.pos.theme.RemoteTheme.refreshBlocking(requireContext().getApplicationContext());
            AppExecutors.main().post(() -> {
                if (binding == null) return;
                binding.inputAppName.setText(prefs.getThemeAppName());
                binding.textAppNameStatus.setText("Current: " + prefs.getThemeAppName());
                if (changed) applyAppNameToShell();
            });
        });
    }

    private void saveAppName() {
        String name = binding.inputAppName.getText() == null
                ? "" : binding.inputAppName.getText().toString().trim();
        if (name.isEmpty() || name.length() > 40) {
            binding.textAppNameStatus.setText("Enter a name between 1 and 40 characters.");
            return;
        }
        binding.buttonSaveAppName.setEnabled(false);
        binding.textAppNameStatus.setText("Saving…");
        AppExecutors.io().execute(() -> {
            org.json.JSONObject body = new org.json.JSONObject();
            try { body.put("app_name", name); } catch (org.json.JSONException ignored) { }
            com.quicktap.pos.net.ApiResponse res = com.quicktap.pos.net.ApiClient.post(
                    requireContext().getApplicationContext(), "v1/settings/app-name", body, true);
            AppExecutors.main().post(() -> {
                if (binding == null) return;
                binding.buttonSaveAppName.setEnabled(true);
                if (res.success) {
                    prefs.setThemeAppName(name);
                    org.json.JSONObject theme = res.dataOrEmpty().optJSONObject("theme");
                    if (theme != null) {
                        com.quicktap.pos.theme.RemoteTheme.apply(requireContext().getApplicationContext(), theme);
                    }
                    binding.textAppNameStatus.setText("Current: " + prefs.getThemeAppName());
                    applyAppNameToShell();
                    toast("App name updated");
                } else {
                    binding.textAppNameStatus.setText(res.message == null || res.message.isEmpty()
                            ? "Could not save the app name." : res.message);
                }
            });
        });
    }

    /** Refreshes the toolbar (and therefore the visible app name) right away. */
    private void applyAppNameToShell() {
        if (getActivity() instanceof com.quicktap.pos.ui.MainActivity) {
            ((com.quicktap.pos.ui.MainActivity) getActivity()).refreshAppName();
        }
    }

    // ------------------------------------------------------- receipt design

    private void setUpReceiptDesign() {
        logoPicker = registerForActivityResult(
                new ActivityResultContracts.GetContent(), this::saveLogo);

        binding.switchReceiptLogo.setChecked(prefs.isReceiptLogoEnabled());
        binding.switchReceiptNote.setChecked(prefs.isReceiptNoteEnabled());
        binding.inputReceiptNote.setText(prefs.getReceiptNote());
        refreshLogoStatus();

        binding.switchReceiptLogo.setOnCheckedChangeListener((v, checked) -> {
            prefs.setReceiptLogoEnabled(checked);
            refreshLogoStatus();
        });
        binding.switchReceiptNote.setOnCheckedChangeListener((v, checked) ->
                prefs.setReceiptNoteEnabled(checked));
        binding.buttonReceiptLogo.setOnClickListener(v -> logoPicker.launch("image/*"));
    }

    /** Copies the picked image into app storage so printing works offline. */
    private void saveLogo(@Nullable Uri uri) {
        if (uri == null) return;
        AppExecutors.io().execute(() -> {
            boolean ok = false;
            java.io.File target = new java.io.File(requireContext().getFilesDir(), "receipt_logo.png");
            try (java.io.InputStream in = requireContext().getContentResolver().openInputStream(uri);
                 java.io.OutputStream out = new java.io.FileOutputStream(target)) {
                if (in != null) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
                    ok = true;
                }
            } catch (Exception ignored) { }
            boolean saved = ok;
            AppExecutors.main().post(() -> {
                if (binding == null) return;
                if (saved) {
                    prefs.setReceiptLogoPath(target.getAbsolutePath());
                    prefs.setReceiptLogoEnabled(true);
                    binding.switchReceiptLogo.setChecked(true);
                    refreshLogoStatus();
                    Toast.makeText(requireContext(), "Logo saved", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), "Could not read that image",
                            Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void refreshLogoStatus() {
        String path = prefs.getReceiptLogoPath();
        boolean has = path != null && !path.isEmpty() && new java.io.File(path).exists();
        binding.textReceiptLogoStatus.setText(has
                ? "Logo ready · printed at the top of every slip"
                : "No logo selected yet. A simple black and white image prints best.");
    }


    // ------------------------------------------------------------- security

    private void setUpSecurity() {
        boolean available = BiometricGate.isAvailable(requireContext());
        binding.switchFingerprint.setEnabled(available);
        binding.switchFingerprint.setChecked(session.isFingerprintEnabled() && available);
        binding.textFingerprintHint.setText(available
                ? "Unlock the locked session with your fingerprint."
                : BiometricGate.unavailableReason(requireContext()));

        binding.switchFingerprint.setOnCheckedChangeListener((v, checked) -> {
            if (checked && !BiometricGate.isAvailable(requireContext())) {
                binding.switchFingerprint.setChecked(false);
                toast(BiometricGate.unavailableReason(requireContext()));
                return;
            }
            session.setFingerprintEnabled(checked,
                    (ok, message, code) -> toast(checked ? "Fingerprint unlock on" : "Fingerprint unlock off"));
        });

        binding.inputAutoLock.setText(String.valueOf(session.autoLockMinutes()));
        binding.textDeviceBinding.setText("This device is bound to your account\n"
                + DeviceIdentity.name());
    }

    // ---------------------------------------------------------------- cloud

    private void setUpCloud() {
        refreshCloudStatus();

        // Sync is customer-triggered by default; the switch opts into background runs.
        binding.switchAutoSync.setChecked(prefs.isAutoSyncEnabled());
        binding.switchAutoSync.setOnCheckedChangeListener((v, checked) -> {
            prefs.setAutoSyncEnabled(checked);
            SyncWorker.applySchedule(requireContext().getApplicationContext());
            toast(checked ? "Auto sync on" : "Auto sync off — use Sync now");
        });

        binding.switchWeeklyBackup.setChecked(prefs.isWeeklyBackupEnabled());
        binding.switchWeeklyBackup.setOnCheckedChangeListener((v, checked) -> {
            prefs.setWeeklyBackupEnabled(checked);
            if (checked) BackupWorker.schedule(requireContext().getApplicationContext());
            else BackupWorker.cancel(requireContext().getApplicationContext());
            toast(checked ? "Weekly backup on" : "Weekly backup off");
        });

        binding.buttonSyncNow.setOnClickListener(v -> {
            binding.buttonSyncNow.setEnabled(false);
            SyncEngine.syncNow(requireContext().getApplicationContext(), (ok, message) -> {
                if (!isAdded()) return;
                binding.buttonSyncNow.setEnabled(true);
                toast(message);
                refreshCloudStatus();
            });
        });
        binding.buttonDriveConnect.setOnClickListener(v -> {
            if (DriveBackupManager.isConnected(requireContext())) {
                DriveBackupManager.disconnect(requireContext(), (ok, message) -> {
                    toast(message);
                    refreshCloudStatus();
                });
            } else {
                driveSignIn.launch(DriveBackupManager.signInIntent(requireContext()));
            }
        });
        binding.buttonDriveBackup.setOnClickListener(v -> {
            binding.buttonDriveBackup.setEnabled(false);
            DriveBackupManager.backupNow(requireActivity(), (ok, message) -> {
                if (!isAdded()) return;
                binding.buttonDriveBackup.setEnabled(true);
                toast(message);
                refreshCloudStatus();
            });
        });
    }

    private void refreshCloudStatus() {
        if (!isAdded() || binding == null) return;
        long lastSync = prefs.getLastSyncAt();
        binding.textSyncStatus.setText(lastSync == 0
                ? "Never synced — data is stored offline until the first sync"
                : "Last sync: " + DateUtil.receipt(lastSync));

        boolean connected = DriveBackupManager.isConnected(requireContext());
        String email = DriveBackupManager.connectedEmail(requireContext());
        long lastBackup = prefs.getLastBackupAt();
        binding.textDriveStatus.setText(!connected
                ? "Google Drive not connected"
                : "Connected: " + (email == null ? "Google account" : email)
                + (lastBackup == 0 ? "\nNo backup yet" : "\nLast backup: " + DateUtil.receipt(lastBackup)));
        binding.buttonDriveConnect.setText(connected ? "Disconnect Google Drive" : "Connect Google Drive");
        binding.buttonDriveBackup.setEnabled(connected);
    }

    private void signOut() {
        session.logout((ok, message, code) -> {
            startActivity(new Intent(requireContext(), LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            requireActivity().finish();
        });
    }

    private void save() {
        prefs.setStoreName(text(binding.inputStoreName.getText()));
        prefs.setStorePhone(text(binding.inputPhone.getText()));
        prefs.setStoreAddress(text(binding.inputAddress.getText()));
        prefs.setCurrency(text(binding.inputCurrency.getText()));
        prefs.setReceiptFooter(text(binding.inputFooter.getText()));
        prefs.setReceiptNote(text(binding.inputReceiptNote.getText()));
        prefs.setInvoicePrefix(text(binding.inputInvoicePrefix.getText()));
        prefs.setTaxPercent(parse(text(binding.inputTax.getText())));
        prefs.setDiscountPercent(parse(text(binding.inputDiscount.getText())));
        session.setAutoLockMinutes((int) parse(text(binding.inputAutoLock.getText())));
        Toast.makeText(requireContext(), "Settings saved", Toast.LENGTH_SHORT).show();
    }


    private void doBackup(Uri uri) {
        if (uri == null) return;
        AppExecutors.io().execute(() -> {
            try {
                BackupUtil.backupTo(requireContext(), uri);
                AppExecutors.main().post(() -> toast("Backup saved"));
            } catch (Exception e) {
                AppExecutors.main().post(() -> toast("Backup failed: " + e.getMessage()));
            }
        });
    }

    private void doRestore(Uri uri) {
        if (uri == null) return;
        AppExecutors.io().execute(() -> {
            try {
                BackupUtil.restoreFrom(requireContext(), uri);
                AppExecutors.main().post(() -> toast("Restored. Please reopen the app."));
            } catch (Exception e) {
                AppExecutors.main().post(() -> toast("Restore failed: " + e.getMessage()));
            }
        });
    }

    private void toast(String message) {
        if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }

    private float parse(String value) {
        try { return Float.parseFloat(value); } catch (Exception e) { return 0f; }
    }

    private String text(CharSequence value) { return value == null ? "" : value.toString().trim(); }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
