package com.quicktap.pos.ui.plans;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.quicktap.pos.R;
import com.quicktap.pos.databinding.ActivityPlansBinding;
import com.quicktap.pos.net.ApiClient;
import com.quicktap.pos.net.ApiResponse;
import com.quicktap.pos.util.AppExecutors;
import com.quicktap.pos.util.AppPrefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

/** Premium in-app plan store: compare, pick a cycle and request the upgrade. */
public class PlansActivity extends AppCompatActivity {

    private ActivityPlansBinding binding;
    private PlanAdapter adapter;
    private List<PlanCatalog.Plan> plans;
    private AppPrefs prefs;
    private boolean yearly;
    private int selected = 1;
    /** Active subscription published by the admin, or null when none is active. */
    private JSONObject current;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlansBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        prefs = AppPrefs.get(this);
        plans = PlanCatalog.load(this);

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.textCurrentPlan.setText("Current licence · " + prefs.getLicenseStatus()
                + " · " + prefs.getStoreName());

        adapter = new PlanAdapter(plans, prefs.getCurrency(), selected, position -> {
            selected = position;
            renderBar();
        });
        binding.recyclerPlans.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerPlans.setNestedScrollingEnabled(false);
        binding.recyclerPlans.setAdapter(adapter);

        binding.toggleCycle.check(binding.buttonMonthly.getId());
        binding.toggleCycle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            yearly = checkedId == binding.buttonYearly.getId();
            adapter.setCycle(yearly);
            renderBar();
        });

        // Purchases were removed: the plan is assigned by the administrator only.
        binding.buttonBuy.setVisibility(View.GONE);
        binding.toggleCycle.setVisibility(View.GONE);
        binding.textPlansFootnote.setText(R.string.plans_admin_managed);
        renderBar();
        applyCurrent();
        refreshFromServer();
    }

    /** Pulls the live plan store from the admin panel and repaints the list. */
    private void refreshFromServer() {
        AppExecutors.io().execute(() -> {
            final List<PlanCatalog.Plan> fresh;
            List<PlanCatalog.Plan> parsed = null;
            JSONObject active = null;
            try {
                ApiResponse res = ApiClient.get(this, "v1/plans", null, true);
                if (res != null && res.success && res.data != null) {
                    JSONArray array = res.data.optJSONArray("plans");
                    if (array != null && array.length() > 0) {
                        parsed = PlanCatalog.parse(array.toString());
                        AppPrefs.get(this).setPlanCatalog(array.toString());
                    }
                    active = res.data.optJSONObject("current");
                    prefs.setCurrentPlan(active == null ? "" : active.toString());
                }
            } catch (Exception ignored) { }
            fresh = parsed;
            final JSONObject freshCurrent = active;
            AppExecutors.main().post(() -> {
                if (binding == null) return;
                if (fresh != null && !fresh.isEmpty()) {
                    plans = fresh;
                    selected = Math.min(selected, plans.size() - 1);
                    adapter.submit(plans, selected);
                }
                current = freshCurrent;
                renderBar();
                applyCurrent();
            });
        });
    }

    /**
     * With an active subscription the catalogue is hidden: the user only sees
     * their own plan and can extend it. Otherwise the full store is shown.
     */
    private void applyCurrent() {
        if (current == null) {
            String cached = prefs.getCurrentPlan();
            if (cached != null && !cached.trim().isEmpty()) {
                try { current = new JSONObject(cached); } catch (Exception ignored) { }
            }
        }
        PlanCatalog.Plan active = null;
        if (current != null) {
            String code = current.optString("code", "");
            for (PlanCatalog.Plan plan : plans) {
                if (plan.code != null && plan.code.equalsIgnoreCase(code)) { active = plan; break; }
            }
        }
        boolean hasActive = active != null;

        binding.cardActivePlan.setVisibility(hasActive ? View.VISIBLE : View.GONE);
        binding.recyclerPlans.setVisibility(View.GONE);
        binding.textPlansFootnote.setVisibility(View.VISIBLE);
        
        binding.buttonBuy.setVisibility(View.GONE);
        binding.cardActivePlan.setVisibility(hasActive ? View.VISIBLE : View.GONE);
        if (!hasActive) {
            binding.textCurrentPlan.setText(R.string.plans_none);
        }

        if (!hasActive) return;

        selected = plans.indexOf(active);
        adapter.submit(plans, selected);
        binding.textActivePlanName.setText(active.name);

        String endsAt = current.optString("ends_at", "");
        int daysLeft = current.optInt("days_left", -1);
        StringBuilder meta = new StringBuilder(active.tagline == null ? "" : active.tagline);
        if (endsAt != null && !endsAt.isEmpty() && !"null".equals(endsAt)) {
            if (meta.length() > 0) meta.append('\n');
            meta.append("Renews on ").append(endsAt.substring(0, Math.min(10, endsAt.length())));
            if (daysLeft >= 0) meta.append(" · ").append(daysLeft).append(" days left");
        }
        binding.textActivePlanMeta.setText(meta.toString());

        binding.containerActiveFeatures.removeAllViews();
        if (active.features != null) {
            for (String feature : active.features) {
                TextView row = new TextView(this);
                row.setText("•  " + feature);
                row.setTextSize(13f);
                row.setTextColor(com.quicktap.pos.theme.RemoteTheme.textPrimary(this));
                int pad = Math.round(getResources().getDisplayMetrics().density * 3);
                row.setPadding(0, pad, 0, pad);
                binding.containerActiveFeatures.addView(row);
            }
        }
        renderBar();
    }

    /** Read-only summary bar: plan name and how long it still runs. */
    private void renderBar() {
        if (current == null) {
            binding.textSelectedName.setText(R.string.plans_none);
            binding.textSelectedPrice.setText("");
            return;
        }
        binding.textSelectedName.setText(current.optString("code", "").toUpperCase(Locale.getDefault())
                + " · " + current.optString("status", "inactive"));
        int daysLeft = current.optInt("days_left", -1);
        binding.textSelectedPrice.setText(daysLeft >= 0
                ? daysLeft + " days left" : getString(R.string.plans_no_expiry));
    }


}
