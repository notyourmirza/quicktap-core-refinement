package com.quicktap.pos.ui.plans;

import android.content.Context;

import com.quicktap.pos.util.AppPrefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Static plan catalogue shown in the in-app store. Purchases go to the admin. */
public final class PlanCatalog {

    public static final class Plan {
        public final String code;
        public final String name;
        public final String tagline;
        public final String tag;
        public final int monthly;
        public final int yearly;
        public final String[] features;

        Plan(String code, String name, String tagline, String tag,
             int monthly, int yearly, String[] features) {
            this.code = code;
            this.name = name;
            this.tagline = tagline;
            this.tag = tag;
            this.monthly = monthly;
            this.yearly = yearly;
            this.features = features;
        }

        public int price(boolean yearlyCycle) { return yearlyCycle ? yearly : monthly; }
    }

    private PlanCatalog() { }

    /**
     * Plans exactly as the Super Admin published them (cached from /v1/plans),
     * falling back to the bundled catalogue when the device has never synced.
     */
    public static List<Plan> load(Context context) {
        String json = AppPrefs.get(context).getPlanCatalog();
        if (json != null && !json.trim().isEmpty()) {
            try {
                List<Plan> parsed = parse(json);
                if (!parsed.isEmpty()) return parsed;
            } catch (Exception ignored) { }
        }
        return all();
    }

    /** Parses the `plans` array returned by the admin API. */
    public static List<Plan> parse(String json) throws Exception {
        JSONArray array = new JSONArray(json);
        List<Plan> out = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.getJSONObject(i);
            JSONArray f = o.optJSONArray("features");
            String[] features = new String[f == null ? 0 : f.length()];
            for (int j = 0; j < features.length; j++) features[j] = f.optString(j, "");
            String tag = o.optString("tag", "");
            out.add(new Plan(
                    o.optString("code", "plan_" + i),
                    o.optString("name", "Plan"),
                    o.optString("tagline", ""),
                    tag.isEmpty() || "null".equals(tag) ? null : tag,
                    (int) Math.round(o.optDouble("price", 0)),
                    (int) Math.round(o.optDouble("price_yearly", 0)),
                    features));
        }
        return out;
    }

    public static List<Plan> all() {
        List<Plan> plans = new ArrayList<>();
        plans.add(new Plan("starter", "Starter", "For a single counter finding its rhythm.", null,
                1500, 15000, new String[]{
                "1 billing counter",
                "Unlimited products & categories",
                "Bluetooth thermal printing",
                "Daily sales summary",
                "Email support"}));

        plans.add(new Plan("growth", "Growth", "Busy kitchens with reporting and cloud backup.", "MOST POPULAR",
                3500, 35000, new String[]{
                "3 billing counters",
                "Cloud sync & auto backup",
                "Advanced reports & exports",
                "Staff roles and shift locks",
                "Priority WhatsApp support"}));

        plans.add(new Plan("enterprise", "Enterprise", "Multi-branch chains with custom branding.", "BEST VALUE",
                7500, 75000, new String[]{
                "Unlimited counters & branches",
                "Custom brand colours & receipts",
                "Dedicated account manager",
                "API access & integrations",
                "99.9% uptime commitment"}));
        return plans;
    }
}
