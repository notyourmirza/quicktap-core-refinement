package com.quicktap.pos.data;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.quicktap.pos.data.dao.BillDao;
import com.quicktap.pos.data.dao.CategoryDao;
import com.quicktap.pos.data.dao.ProductDao;
import com.quicktap.pos.data.entity.Bill;
import com.quicktap.pos.data.entity.BillItem;
import com.quicktap.pos.data.entity.Category;
import com.quicktap.pos.data.entity.Product;
import com.quicktap.pos.data.model.CategorySales;
import com.quicktap.pos.data.model.DaySummary;
import com.quicktap.pos.data.model.ProductSales;
import com.quicktap.pos.sync.SyncEngine;
import com.quicktap.pos.util.AppExecutors;
import com.quicktap.pos.util.AppPrefs;

import java.util.List;
import java.util.Locale;

/**
 * Single source of truth for all offline data. ViewModels talk only to this class;
 * nothing else in the app touches a DAO directly.
 */
public class PosRepository {

    private final ProductDao productDao;
    private final CategoryDao categoryDao;
    private final BillDao billDao;
    private final AppPrefs prefs;
    private final Context context;

    public PosRepository(Context context) {
        this.context = context.getApplicationContext();
        AppDatabase db = AppDatabase.get(context);
        this.productDao = db.productDao();
        this.categoryDao = db.categoryDao();
        this.billDao = db.billDao();
        this.prefs = AppPrefs.get(context);
        AppExecutors.io().execute(this::seedIfEmpty);
    }

    /** First launch: create a small starter catalogue so the grid is never blank. */
    private void seedIfEmpty() {
        if (categoryDao.count() > 0) return;
        long fastFood = categoryDao.insert(new Category("Fast Food", 1));
        long drinks = categoryDao.insert(new Category("Drinks", 2));
        long dessert = categoryDao.insert(new Category("Dessert", 3));
        addSeed("Classic Burger", 250, fastFood);
        addSeed("Cheese Burger", 320, fastFood);
        addSeed("Chicken Pizza", 750, fastFood);
        addSeed("French Fries", 150, fastFood);
        addSeed("Milk Tea", 60, drinks);
        addSeed("Cold Coffee", 180, drinks);
        addSeed("Fresh Lime", 90, drinks);
        addSeed("Chocolate Cake", 220, dessert);
    }

    private void addSeed(String name, double price, long categoryId) {
        Product p = new Product();
        p.name = name;
        p.price = price;
        p.categoryId = categoryId;
        productDao.insert(p);
    }

    // ---------------- products ----------------

    public LiveData<List<Product>> observeProducts(long categoryId, String query) {
        return productDao.observeFiltered(categoryId, query == null ? "" : query.trim());
    }

    public LiveData<List<Product>> observeAllProducts() { return productDao.observeAll(); }

    public void saveProduct(Product product, Runnable done) {
        AppExecutors.io().execute(() -> {
            stampForSync(product);
            if (product.id == 0) productDao.insert(product);
            else productDao.update(product);
            if (done != null) AppExecutors.main().post(done);
            SyncEngine.syncAuto(context);
        });
    }

    public void deleteProduct(Product product) {
        AppExecutors.io().execute(() -> {
            // Soft delete first so the removal replicates, then drop it locally.
            product.deleted = true;
            stampForSync(product);
            productDao.update(product);
            SyncEngine.syncAuto(context);
        });
    }

    // ---------------- categories ----------------

    public LiveData<List<Category>> observeCategories() { return categoryDao.observeAll(); }

    public List<Category> categoriesBlocking() { return categoryDao.getAll(); }

    public void saveCategory(Category category) {
        AppExecutors.io().execute(() -> {
            stampForSync(category);
            if (category.id == 0) categoryDao.insert(category);
            else categoryDao.update(category);
            SyncEngine.syncAuto(context);
        });
    }

    public void deleteCategory(Category category) {
        AppExecutors.io().execute(() -> {
            category.deleted = true;
            stampForSync(category);
            categoryDao.update(category);
            SyncEngine.syncAuto(context);
        });
    }

    // ---------------- bills ----------------

    public LiveData<List<Bill>> observeRecentBills(int limit) {
        return billDao.observeRecent(limit);
    }

    public LiveData<DaySummary> observeSummary(long from, long to) {
        return billDao.observeSummary(from, to);
    }

    public DaySummary summary(long from, long to) { return billDao.summary(from, to); }

    public List<ProductSales> salesByProduct(long from, long to) {
        return billDao.salesByProduct(from, to);
    }

    public List<CategorySales> salesByCategory(long from, long to) {
        return billDao.salesByCategory(from, to);
    }

    public List<BillItem> itemsOf(long billId) { return billDao.itemsOf(billId); }

    public Bill billById(long id) { return billDao.byId(id); }

    public Bill lastBill() { return billDao.last(); }

    public void deleteBill(long id) {
        AppExecutors.io().execute(() -> billDao.deleteBillCascade(id));
    }

    /** Runs on the caller's (background) thread and returns the persisted id. */
    public long saveBillBlocking(Bill bill, List<BillItem> items) {
        bill.invoiceNo = nextInvoiceNo();
        if (bill.uuid == null || bill.uuid.isEmpty()) bill.uuid = SyncEngine.newUuid();
        bill.updatedAt = System.currentTimeMillis();
        bill.dirty = true;
        long id = billDao.saveBill(bill, items);
        for (BillItem item : items) {
            productDao.bumpSold(item.productId, item.qty);
            productDao.reduceStock(item.productId, item.qty);
        }
        // Push the sale as soon as possible; it stays queued when offline.
        SyncEngine.syncAuto(context);
        return id;
    }

    /** Gives any locally edited row the sync metadata the server needs. */
    private void stampForSync(Product product) {
        if (product.uuid == null || product.uuid.isEmpty()) product.uuid = SyncEngine.newUuid();
        product.updatedAt = System.currentTimeMillis();
        product.dirty = true;
    }

    private void stampForSync(Category category) {
        if (category.uuid == null || category.uuid.isEmpty()) category.uuid = SyncEngine.newUuid();
        category.updatedAt = System.currentTimeMillis();
        category.dirty = true;
    }

    private String nextInvoiceNo() {
        int next = prefs.nextInvoiceSeq();
        return String.format(Locale.US, "%s%06d", prefs.getInvoicePrefix(), next);
    }
}
