package com.quicktap.pos.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.quicktap.pos.data.model.CartLine;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Parked (held) bills. A cashier can hold the current cart, serve someone else,
 * and resume it later. Stored as JSON in SharedPreferences so a crash or an app
 * restart never loses a table's order.
 */
public class HeldOrders {

    /** One parked cart. */
    public static class Held {
        public String id;
        public String label;
        public long createdAt;
        public List<CartLine> lines = new ArrayList<>();

        public int itemCount() {
            int n = 0;
            for (CartLine line : lines) n += line.qty;
            return n;
        }

        public double total() {
            double sum = 0;
            for (CartLine line : lines) sum += line.total();
            return sum;
        }
    }

    private static final String FILE = "quicktap_held_orders";
    private static final String KEY = "orders";
    private static HeldOrders instance;

    private final SharedPreferences sp;

    private HeldOrders(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static synchronized HeldOrders get(Context context) {
        if (instance == null) instance = new HeldOrders(context);
        return instance;
    }

    public List<Held> all() {
        List<Held> out = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(sp.getString(KEY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                Held held = new Held();
                held.id = object.optString("id");
                held.label = object.optString("label");
                held.createdAt = object.optLong("createdAt");
                JSONArray rows = object.optJSONArray("lines");
                if (rows != null) {
                    for (int j = 0; j < rows.length(); j++) {
                        JSONObject row = rows.getJSONObject(j);
                        held.lines.add(new CartLine(
                                row.optLong("productId"),
                                row.optString("name"),
                                row.optString("categoryName", null),
                                row.optDouble("price"),
                                Math.max(1, row.optInt("qty", 1))));
                    }
                }
                if (!held.lines.isEmpty()) out.add(held);
            }
        } catch (Exception ignored) {
            // Corrupt payload: fall through with whatever parsed cleanly.
        }
        return out;
    }

    public int count() {
        return all().size();
    }

    /** Parks a copy of the cart and returns its generated id. */
    public String hold(String label, List<CartLine> lines) {
        if (lines == null || lines.isEmpty()) return null;
        List<Held> current = all();
        Held held = new Held();
        held.id = "H" + System.currentTimeMillis();
        held.label = label == null || label.trim().isEmpty()
                ? "Order " + (current.size() + 1) : label.trim();
        held.createdAt = System.currentTimeMillis();
        held.lines.addAll(lines);
        current.add(held);
        persist(current);
        return held.id;
    }

    public void remove(String id) {
        List<Held> current = all();
        for (int i = current.size() - 1; i >= 0; i--) {
            if (current.get(i).id.equals(id)) current.remove(i);
        }
        persist(current);
    }

    public void clearAll() {
        sp.edit().remove(KEY).apply();
    }

    private void persist(List<Held> orders) {
        JSONArray array = new JSONArray();
        try {
            for (Held held : orders) {
                JSONObject object = new JSONObject();
                object.put("id", held.id);
                object.put("label", held.label);
                object.put("createdAt", held.createdAt);
                JSONArray rows = new JSONArray();
                for (CartLine line : held.lines) {
                    JSONObject row = new JSONObject();
                    row.put("productId", line.productId);
                    row.put("name", line.name);
                    row.put("categoryName", line.categoryName);
                    row.put("price", line.price);
                    row.put("qty", line.qty);
                    rows.put(row);
                }
                object.put("lines", rows);
                array.put(object);
            }
        } catch (Exception ignored) {
        }
        sp.edit().putString(KEY, array.toString()).apply();
    }
}
