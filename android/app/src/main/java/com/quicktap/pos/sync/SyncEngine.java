package com.quicktap.pos.sync;

import android.content.Context;

import com.quicktap.pos.data.AppDatabase;
import com.quicktap.pos.data.dao.CategoryDao;
import com.quicktap.pos.data.dao.ProductDao;
import com.quicktap.pos.data.dao.SyncDao;
import com.quicktap.pos.data.entity.Bill;
import com.quicktap.pos.data.entity.BillItem;
import com.quicktap.pos.data.entity.Category;
import com.quicktap.pos.data.entity.Product;
import com.quicktap.pos.net.ApiClient;
import com.quicktap.pos.net.ApiResponse;
import com.quicktap.pos.theme.RemoteTheme;
import com.quicktap.pos.util.AppExecutors;
import com.quicktap.pos.util.AppPrefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Offline-first two-way sync.
 *
 * PUSH: every locally changed row (dirty = 1) is uploaded to /v1/sync/push. The
 *       server upserts by uuid so retries are idempotent.
 * PULL: /v1/sync/pull?since=<lastSync> returns catalogue rows changed on other
 *       devices plus the server-driven theme / settings, which are applied here.
 *
 * Conflicts use last-write-wins on updatedAt; rows the server rejects stay
 * dirty and are retried on the next run.
 */
public final class SyncEngine {

    public interface Listener { void onFinished(boolean ok, String message); }

    private static final int BATCH = 300;
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private SyncEngine() { }

    /**
     * Manual sync — only ever started by the user tapping "Sync now".
     * Automatic background syncing is disabled by default because repeated
     * unattended runs were re-uploading old rows and duplicating data.
     */
    public static void syncNow(Context context, Listener listener) {
        AppExecutors.io().execute(() -> {
            boolean[] out = runBlocking(context);
            if (listener != null) {
                AppExecutors.main().post(() -> listener.onFinished(out[0],
                        out[0] ? "Sync complete" : "Sync failed — check your connection"));
            }
        });
    }

    /**
     * Called from app lifecycle hooks (checkout, resume, splash). It is a no-op
     * unless the shop explicitly turned automatic sync back on in Settings.
     */
    public static void syncAuto(Context context) {
        if (!AppPrefs.get(context).isAutoSyncEnabled()) return;
        syncNow(context, null);
    }

    /** @return [ok] — safe to call from any background thread. */
    public static boolean[] runBlocking(Context context) {
        if (!RUNNING.compareAndSet(false, true)) return new boolean[]{true};
        try {
            AppPrefs prefs = AppPrefs.get(context);
            if (prefs.getAccessToken() == null) return new boolean[]{false};
            if (!ApiClient.isOnline(context)) return new boolean[]{false};

            AppDatabase db = AppDatabase.get(context);
            SyncDao sync = db.syncDao();
            backfill(sync);

            boolean pushed = push(context, db, sync);
            long serverTime = pull(context, db, sync, prefs);
            boolean ok = pushed && serverTime > 0;
            // The cursor MUST be the server clock. Using the device clock made a
            // skewed phone re-pull (and re-insert) rows it already had.
            if (ok) prefs.setLastSyncAt(serverTime);
            return new boolean[]{ok};
        } catch (Exception e) {
            return new boolean[]{false};
        } finally {
            RUNNING.set(false);
        }
    }

    // ------------------------------------------------------------------ push

    private static boolean push(Context ctx, AppDatabase db, SyncDao sync) throws Exception {
        List<Category> categories = sync.dirtyCategories(BATCH);
        List<Product> products = sync.dirtyProducts(BATCH);
        List<Bill> bills = sync.dirtyBills(BATCH);
        if (categories.isEmpty() && products.isEmpty() && bills.isEmpty()) return true;

        JSONObject body = new JSONObject();

        JSONArray cats = new JSONArray();
        for (Category c : categories) {
            c.uuid = persistUuid(sync, c);
            cats.put(new JSONObject()
                    .put("uuid", c.uuid)
                    .put("name", c.name)
                    .put("sort_order", c.position)
                    .put("updated_at", stamp(c.updatedAt))
                    .put("deleted_at", c.deleted ? stamp(c.updatedAt) : JSONObject.NULL));
        }
        body.put("categories", cats);

        JSONArray prods = new JSONArray();
        for (Product p : products) {
            p.uuid = persistUuid(sync, p);
            prods.put(new JSONObject()
                    .put("uuid", p.uuid)
                    .put("category_uuid", sync.categoryUuid(p.categoryId))
                    .put("name", p.name)
                    .put("price", p.price)
                    .put("barcode", p.barcode == null ? JSONObject.NULL : p.barcode)
                    .put("stock", p.stock)
                    .put("is_active", p.available ? 1 : 0)
                    .put("updated_at", stamp(p.updatedAt))
                    .put("deleted_at", p.deleted ? stamp(p.updatedAt) : JSONObject.NULL));
        }
        body.put("products", prods);

        JSONArray orders = new JSONArray();
        for (Bill b : bills) {
            JSONArray items = new JSONArray();
            for (BillItem i : sync.itemsOf(b.id)) {
                items.put(new JSONObject()
                        .put("name", i.name)
                        .put("price", i.price)
                        .put("qty", i.qty)
                        .put("line_total", i.lineTotal()));
            }
            b.uuid = persistUuid(sync, b);
            orders.put(new JSONObject()
                    .put("uuid", b.uuid)
                    .put("invoice_no", b.invoiceNo)
                    .put("order_type", b.orderType)
                    .put("customer_name", b.customerName == null ? JSONObject.NULL : b.customerName)
                    .put("customer_phone", b.customerPhone == null ? JSONObject.NULL : b.customerPhone)
                    .put("subtotal", b.subtotal)
                    .put("discount", b.discount)
                    .put("tax", b.tax)
                    .put("total", b.total)
                    .put("status", b.paid ? "paid" : "unpaid")
                    .put("created_at", stamp(b.createdAt))
                    .put("updated_at", stamp(b.updatedAt == 0 ? b.createdAt : b.updatedAt))
                    .put("items", items));
        }
        body.put("orders", orders);

        ApiResponse res = ApiClient.post(ctx, "v1/sync/push", body, true);
        if (!res.success || res.data == null) return false;

        JSONObject accepted = res.data.optJSONObject("accepted");
        if (accepted == null) return true;
        markSynced(sync, accepted.optJSONArray("categories"), sync::markCategorySynced);
        markSynced(sync, accepted.optJSONArray("products"), sync::markProductSynced);
        markSynced(sync, accepted.optJSONArray("orders"), sync::markBillSynced);
        return true;
    }

    private interface Marker { void mark(String uuid); }

    private static void markSynced(SyncDao dao, JSONArray uuids, Marker marker) {
        if (uuids == null) return;
        for (int i = 0; i < uuids.length(); i++) {
            String uuid = uuids.optString(i, null);
            if (uuid != null && !uuid.isEmpty()) marker.mark(uuid);
        }
    }

    // ------------------------------------------------------------------ pull

    /** @return the server clock in millis, or 0 when the pull failed. */
    private static long pull(Context ctx, AppDatabase db, SyncDao sync, AppPrefs prefs) {
        Map<String, String> query = new HashMap<>();
        query.put("since", String.valueOf(prefs.getLastSyncAt()));

        ApiResponse res = ApiClient.get(ctx, "v1/sync/pull", query, true);
        if (!res.success || res.data == null) return 0L;

        JSONObject changes = res.data.optJSONObject("changes");
        if (changes != null) {
            applyCategories(db.categoryDao(), sync, changes.optJSONArray("categories"));
            applyProducts(db.productDao(), sync, changes.optJSONArray("products"));
        }

        // Server-driven branding and store settings ride along with the pull.
        RemoteTheme.apply(ctx, res.data.optJSONObject("theme"));
        applySettings(prefs, res.data.optJSONObject("settings"));

        long serverTime = res.data.optLong("server_time", 0L);
        return serverTime > 0 ? serverTime : System.currentTimeMillis();
    }

    private static void applyCategories(CategoryDao dao, SyncDao sync, JSONArray rows) {
        if (rows == null) return;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject r = rows.optJSONObject(i);
            if (r == null) continue;
            String uuid = r.optString("uuid", "");
            if (uuid.isEmpty()) continue;

            if (!r.isNull("deleted_at")) { sync.hardDeleteCategory(uuid); continue; }

            Category local = sync.categoryByUuid(uuid);
            long remoteAt = millis(r.opt("updated_at"));
            String remoteName = r.optString("name", "Category");
            if (local == null) {
                // The same category may already exist locally under a different
                // uuid (older build, restored backup, second device). Adopt the
                // server uuid instead of inserting a second copy.
                local = sync.categoryByName(remoteName);
            }
            if (local == null) {
                Category c = new Category();
                c.uuid = uuid;
                c.name = remoteName;
                c.position = r.optInt("sort_order", 0);
                c.updatedAt = remoteAt;
                c.dirty = false;
                dao.insert(c);
            } else if (!uuid.equals(local.uuid)) {
                sync.setCategoryUuid(local.id, uuid);
                local.uuid = uuid;
                local.name = remoteName;
                local.position = r.optInt("sort_order", local.position);
                local.updatedAt = remoteAt;
                local.dirty = false;
                dao.update(local);
            } else if (remoteAt > local.updatedAt) {
                local.name = r.optString("name", local.name);
                local.position = r.optInt("sort_order", local.position);
                local.updatedAt = remoteAt;
                local.dirty = false;
                dao.update(local);
            }
        }
    }

    private static void applyProducts(ProductDao dao, SyncDao sync, JSONArray rows) {
        if (rows == null) return;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject r = rows.optJSONObject(i);
            if (r == null) continue;
            String uuid = r.optString("uuid", "");
            if (uuid.isEmpty()) continue;

            if (!r.isNull("deleted_at")) { sync.hardDeleteProduct(uuid); continue; }

            Long categoryId = null;
            String catUuid = r.optString("category_uuid", "");
            if (!catUuid.isEmpty()) categoryId = sync.categoryIdByUuid(catUuid);

            Product local = sync.productByUuid(uuid);
            long remoteAt = millis(r.opt("updated_at"));
            String remoteName = r.optString("name", "Product");
            if (local == null) {
                // Adopt an existing local row with the same identity instead of
                // creating a duplicate (this was the main source of the
                // "catalogue grows on every sync" problem).
                String barcode = r.isNull("barcode") ? null : r.optString("barcode");
                if (barcode != null && !barcode.isEmpty()) local = sync.productByBarcode(barcode);
                if (local == null) {
                    local = sync.productByNameAndCategory(remoteName,
                            categoryId == null ? 0 : categoryId);
                }
            }
            if (local == null) {
                Product p = new Product();
                p.uuid = uuid;
                p.name = remoteName;
                p.price = r.optDouble("price", 0);
                p.barcode = r.isNull("barcode") ? null : r.optString("barcode");
                p.stock = r.optInt("stock", -1);
                p.available = r.optInt("is_active", 1) == 1;
                p.categoryId = categoryId == null ? 0 : categoryId;
                p.updatedAt = remoteAt;
                p.dirty = false;
                dao.insert(p);
            } else if (!uuid.equals(local.uuid)) {
                sync.setProductUuid(local.id, uuid);
                local.uuid = uuid;
                local.name = remoteName;
                local.price = r.optDouble("price", local.price);
                local.barcode = r.isNull("barcode") ? local.barcode : r.optString("barcode");
                local.stock = r.optInt("stock", local.stock);
                local.available = r.optInt("is_active", local.available ? 1 : 0) == 1;
                if (categoryId != null) local.categoryId = categoryId;
                local.updatedAt = remoteAt;
                local.dirty = false;
                dao.update(local);
            } else if (remoteAt > local.updatedAt) {
                local.name = r.optString("name", local.name);
                local.price = r.optDouble("price", local.price);
                local.barcode = r.isNull("barcode") ? local.barcode : r.optString("barcode");
                local.stock = r.optInt("stock", local.stock);
                local.available = r.optInt("is_active", local.available ? 1 : 0) == 1;
                if (categoryId != null) local.categoryId = categoryId;
                local.updatedAt = remoteAt;
                local.dirty = false;
                dao.update(local);
            }
        }
    }

    private static void applySettings(AppPrefs prefs, JSONObject settings) {
        if (settings == null) return;
        if (settings.has("auto_lock_minutes")) {
            prefs.setAutoLockMinutes(settings.optInt("auto_lock_minutes", prefs.getAutoLockMinutes()));
        }
        if (settings.has("currency")) {
            String c = settings.optString("currency", "");
            if (!c.isEmpty()) prefs.setCurrency(c);
        }
        if (settings.has("receipt_footer")) {
            String f = settings.optString("receipt_footer", "");
            if (!f.isEmpty()) prefs.setReceiptFooter(f);
        }
        if (settings.has("tax_percent")) {
            prefs.setTaxPercent((float) settings.optDouble("tax_percent", prefs.getTaxPercent()));
        }
    }

    // ----------------------------------------------------------------- utils

    private static void backfill(SyncDao sync) {
        // Rows created before sync existed (or by the first-run seed) have no
        // uuid. Give them one BEFORE pushing, otherwise every push invents a
        // fresh uuid, the server stores a new row, and the next pull inserts a
        // duplicate copy locally - the catalogue would double on every bill.
        sync.backfillCategoryUuids();
        sync.backfillProductUuids();
        sync.backfillBillUuids();
        // Clean up whatever earlier builds already duplicated.
        sync.repointProductsToFirstCategory();
        sync.dedupeCategories();
        sync.dedupeProducts();
        // Same uuid stored twice locally can only ever be a duplicate.
        sync.dedupeCategoriesByUuid();
        sync.dedupeProductsByUuid();
        sync.dedupeBillsByUuid();
        // Orphan bill items left behind by any of the deletes above.
        sync.deleteOrphanBillItems();
    }

    /** Guarantees the row keeps the exact uuid we send to the server. */
    private static String persistUuid(SyncDao sync, Category c) {
        if (c.uuid == null || c.uuid.isEmpty()) {
            c.uuid = newUuid();
            sync.setCategoryUuid(c.id, c.uuid);
        }
        return c.uuid;
    }

    private static String persistUuid(SyncDao sync, Product p) {
        if (p.uuid == null || p.uuid.isEmpty()) {
            p.uuid = newUuid();
            sync.setProductUuid(p.id, p.uuid);
        }
        return p.uuid;
    }

    private static String persistUuid(SyncDao sync, Bill b) {
        if (b.uuid == null || b.uuid.isEmpty()) {
            b.uuid = newUuid();
            sync.setBillUuid(b.id, b.uuid);
        }
        return b.uuid;
    }

    public static String newUuid() { return UUID.randomUUID().toString(); }

    private static long stamp(long millis) { return millis <= 0 ? System.currentTimeMillis() : millis; }

    private static long millis(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number) {
            long v = ((Number) value).longValue();
            return v > 1_000_000_000_000L ? v : v * 1000L;
        }
        try {
            java.text.SimpleDateFormat fmt =
                    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US);
            java.util.Date d = fmt.parse(String.valueOf(value));
            return d == null ? 0L : d.getTime();
        } catch (Exception e) {
            return 0L;
        }
    }
}
