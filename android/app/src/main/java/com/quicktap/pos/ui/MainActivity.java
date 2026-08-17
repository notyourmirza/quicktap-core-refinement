package com.quicktap.pos.ui;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.quicktap.pos.R;
import com.quicktap.pos.auth.SessionManager;
import com.quicktap.pos.license.LicenseGate;
import com.quicktap.pos.license.LicenseService;
import com.quicktap.pos.license.LicenseState;
import com.quicktap.pos.theme.ThemeMode;
import com.quicktap.pos.databinding.ActivityMainBinding;
import com.quicktap.pos.ui.billing.BillingFragment;
import com.quicktap.pos.ui.dashboard.DashboardFragment;
import com.quicktap.pos.ui.history.HistoryFragment;
import com.quicktap.pos.ui.products.ProductsFragment;
import com.quicktap.pos.ui.reports.ReportsFragment;
import com.quicktap.pos.ui.settings.PrinterSettingsActivity;
import com.quicktap.pos.ui.settings.SettingsFragment;
import com.quicktap.pos.util.AppPrefs;

import android.widget.TextView;

/**
 * Application shell. A Material 3 top app bar plus a navigation drawer replaces
 * the old bottom navigation so the point-of-sale surface stays distraction free.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private AppPrefs prefs;
    private String currentTitle = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        prefs = AppPrefs.get(this);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayShowTitleEnabled(false);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, binding.drawerLayout, binding.toolbar,
                R.string.open_menu, R.string.close_menu);
        binding.drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        binding.drawerLayout.setScrimColor(0x66101828);

        binding.navigationView.setNavigationItemSelectedListener(this::onDrawerItem);
        binding.toolbar.setOnMenuItemClickListener(this::onTopBarItem);

        bindDrawerHeader();
        syncThemeIcon();

        if (savedInstanceState == null) {
            binding.navigationView.setCheckedItem(R.id.nav_billing);
            select(R.id.nav_billing);
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START);
                } else if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                    getSupportFragmentManager().popBackStack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        requestBluetoothPermissionIfNeeded();
        enforceAccess();
        // onResume() runs right after onCreate() and owns the licence check,
        // so the gate is asked exactly once per foreground.
    }

    private void bindDrawerHeader() {
        android.view.View header = binding.navigationView.getHeaderView(0);
        applyHeaderInset(header);
        TextView store = header.findViewById(R.id.headerStore);
        TextView user = header.findViewById(R.id.headerUser);
        TextView plan = header.findViewById(R.id.headerPlan);
        store.setText(prefs.getStoreName());
        String name = prefs.getFullName();
        if (name == null || name.trim().isEmpty()) name = prefs.getUsername();
        user.setText((name == null ? "Signed in" : name) + " · " + prefs.getUserRole());
        plan.setText("APPROVED".equalsIgnoreCase(prefs.getLicenseStatus())
                ? getString(R.string.plan_active) : prefs.getLicenseStatus());
    }

    /** Keeps the drawer header clear of the status bar on edge-to-edge devices. */
    private void applyHeaderInset(android.view.View header) {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header, (view, insets) -> {
            int top = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).top;
            view.setPadding(view.getPaddingLeft(), Math.round(20 * getResources()
                            .getDisplayMetrics().density) + top,
                    view.getPaddingRight(), view.getPaddingBottom());
            return insets;
        });
        androidx.core.view.ViewCompat.requestApplyInsets(header);
    }

    private boolean onDrawerItem(MenuItem item) {
        int id = item.getItemId();
        binding.drawerLayout.closeDrawer(GravityCompat.START);
        if (id == R.id.nav_printer) {
            startActivity(new Intent(this, PrinterSettingsActivity.class));
            return true;
        }
        if (id == R.id.nav_plans) { showPlans(); return true; }
        if (id == R.id.nav_notifications) {
            startActivity(new Intent(this,
                    com.quicktap.pos.ui.notifications.NotificationsActivity.class));
            return true;
        }

        if (id == R.id.nav_contact) { contactAdmin(); return true; }
        if (id == R.id.nav_logout) { confirmSignOut(); return true; }
        select(id);
        return true;
    }

    /** Swaps the hosted section and keeps the top app bar copy in sync. */
    private void select(int id) {
        if (id == R.id.nav_dashboard) open(new DashboardFragment(), "Dashboard");
        else if (id == R.id.nav_products) open(new ProductsFragment(), "Catalogue");
        else if (id == R.id.nav_history) open(new HistoryFragment(), "Sales history");
        else if (id == R.id.nav_reports) open(new ReportsFragment(), "Reports");
        else if (id == R.id.nav_settings) open(new SettingsFragment(), "Business settings");
        else open(new BillingFragment(), "Point of sale");
    }

    private void open(Fragment fragment, String subtitle) {
        currentTitle = subtitle;
        applyToolbarTitles();
        show(fragment, false);
    }

    /** Called by Settings after the shop renames the app. */
    public void refreshAppName() {
        applyToolbarTitles();
        bindDrawerHeader();
    }

    /** The toolbar always shows the (server driven) app name plus the section. */
    private void applyToolbarTitles() {
        String name = prefs.getThemeAppName();
        if (name == null || name.trim().isEmpty()) name = prefs.getStoreName();
        binding.toolbar.setTitle(name);
        binding.toolbar.setSubtitle(currentTitle);
    }

    /** Keeps the top bar icon in sync with the active day / night mode. */
    private void syncThemeIcon() {
        MenuItem item = binding.toolbar.getMenu().findItem(R.id.action_theme);
        if (item == null) return;
        boolean dark = ThemeMode.DARK.equals(ThemeMode.current(this));
        item.setIcon(dark ? R.drawable.ic_sun : R.drawable.ic_moon);
        item.setTitle(dark ? R.string.action_light_mode : R.string.action_dark_mode);
    }

    /** Light / Dark / System default — saved locally and applied instantly. */
    private void showThemePicker() {
        final String[] labels = {"Light", "Dark", "System default"};
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.theme_mode_title)
                .setSingleChoiceItems(labels, ThemeMode.index(this), (dialog, which) -> {
                    dialog.dismiss();
                    String chosen = ThemeMode.fromIndex(which);
                    if (chosen.equals(ThemeMode.current(this))) {
                        syncThemeIcon();
                        return;
                    }
                    ThemeMode.set(this, chosen);
                    syncThemeIcon();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private boolean onTopBarItem(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_theme) {
            showThemePicker();
            return true;
        }
        if (id == R.id.action_search) {
            binding.navigationView.setCheckedItem(R.id.nav_products);
            open(new ProductsFragment(), "Catalogue");
            return true;
        }

        if (id == R.id.action_notifications) {
            startActivity(new Intent(this,
                    com.quicktap.pos.ui.notifications.NotificationsActivity.class));
            return true;
        }

        if (id == R.id.action_profile) {
            String name = prefs.getFullName();
            if (name == null || name.trim().isEmpty()) name = prefs.getUsername();
            new MaterialAlertDialogBuilder(this)
                    .setTitle(name == null ? "Account" : name)
                    .setMessage("Role: " + prefs.getUserRole()
                            + "\nStore: " + prefs.getStoreName()
                            + "\nDevice: " + prefs.getDeviceId())
                    .setPositiveButton("Done", null)
                    .setNegativeButton("Sign out", (d, w) -> confirmSignOut())
                    .show();
            return true;
        }
        return false;
    }

    private void showPlans() {
        startActivity(new Intent(this, com.quicktap.pos.ui.plans.PlansActivity.class));
    }

    private void contactAdmin() {
        com.quicktap.pos.ui.support.ContactSheet.show(this, "Technical help");
    }


    private void confirmSignOut() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Sign out?")
                .setMessage("This counter will need to sign in again before taking orders.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Sign out", (d, w) -> {
                    SessionManager.get(this).forceSignOut();
                    startActivity(new Intent(this, LoginActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    finish();
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SessionManager session = SessionManager.get(this);
        if (!session.isSignedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        if (session.shouldLock()) {
            startActivity(new Intent(this, UnlockActivity.class));
            return;
        }
        session.markActivity();
        refreshTheme();
        bindDrawerHeader();
        applyToolbarTitles();
        syncThemeIcon();
        enforceLicense();
    }

    /**
     * Re-asks the SERVER for the licence state on every resume. The shared
     * banner shows "VERIFY YOUR LICENSE / CONTACT FOR LICENSE ACTIVATION" while
     * the account is not active, and the single {@link LicenseGate} routes the
     * user away when the server says the app must stay locked.
     */
    private void enforceLicense() {
        binding.licenseBanner.apply(cachedHint());
        LicenseService.status(this, state -> {
            if (isFinishing()) return;
            binding.licenseBanner.apply(state);
            if (LicenseState.OFFLINE.equals(state.state)) {
                // Offline never activates anything: only an already confirmed
                // ACTIVE licence inside its grace window keeps selling.
                if (!LicenseGate.offlineGraceAllows(this)) LicenseGate.route(this, state);
                return;
            }
            if (LicenseState.ACTIVE.equals(state.state) && state.unlocked && state.confirmed) return;
            LicenseGate.route(this, state);
        });
    }

    /** Cached snapshot used only to paint the banner before the server answers. */
    private LicenseState cachedHint() {
        LicenseState hint = new LicenseState();
        hint.state = prefs.getLicenseState();
        hint.unlocked = LicenseState.ACTIVE.equals(hint.state);
        hint.confirmed = prefs.isLicenseConfirmed();
        return hint;
    }

    /** Pulls the brand colours published by the Super Admin, at most once a minute. */
    private void refreshTheme() {
        long now = System.currentTimeMillis();
        if (now - lastThemeCheck < 60_000L) return;
        lastThemeCheck = now;
        com.quicktap.pos.theme.RemoteTheme.refresh(this, changed -> {
            if (changed && !isFinishing()) recreate();
        });
    }

    private long lastThemeCheck;

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        SessionManager.get(this).markActivity();
    }

    /** Pushes a fragment; secondary screens (like Reports) go on the back stack. */
    public void show(Fragment fragment, boolean addToBackStack) {
        androidx.fragment.app.FragmentTransaction tx = getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(R.anim.fragment_enter, R.anim.fragment_exit,
                        R.anim.fragment_pop_enter, R.anim.fragment_pop_exit)
                .replace(R.id.container, fragment);
        if (addToBackStack) tx.addToBackStack(null);
        tx.commit();
    }

    private void requestBluetoothPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN}, 42);
        }
    }

    /**
     * Re-validates the account with the server. A blocked device, disabled user
     * or suspended shop sends the app to the locked screen.
     */
    private void enforceAccess() {
        SessionManager.get(this).refreshProfile((ok, message, code) -> {
            if (ok) return;
            if ("DEVICE_MISMATCH".equals(code) || "SHOP_INACTIVE".equals(code)
                    || "USER_DISABLED".equals(code) || "SUBSCRIPTION_EXPIRED".equals(code)) {
                startActivity(new Intent(this, LockedActivity.class));
                finish();
            }
        });
    }
}
