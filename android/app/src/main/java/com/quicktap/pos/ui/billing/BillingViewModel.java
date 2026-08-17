package com.quicktap.pos.ui.billing;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.quicktap.pos.QuickTapApp;
import com.quicktap.pos.data.PosRepository;
import com.quicktap.pos.data.entity.Bill;
import com.quicktap.pos.data.entity.BillItem;
import com.quicktap.pos.data.entity.Category;
import com.quicktap.pos.data.entity.Product;
import com.quicktap.pos.data.model.CartLine;
import com.quicktap.pos.util.AppExecutors;
import com.quicktap.pos.util.AppPrefs;

import java.util.ArrayList;
import java.util.List;

/** Holds the live cart and the filtered product grid for the billing screen. */
public class BillingViewModel extends AndroidViewModel {

    /** Filter key encoded as "categoryId|query" so one LiveData drives the grid. */
    private final MutableLiveData<String> filter = new MutableLiveData<>("0|");
    private final MutableLiveData<List<CartLine>> cart = new MutableLiveData<>(new ArrayList<>());
    private final LiveData<List<Product>> products;
    private final LiveData<List<Category>> categories;
    private final PosRepository repo;
    private final AppPrefs prefs;

    private long categoryId = 0;
    private String query = "";

    public BillingViewModel(@NonNull Application application) {
        super(application);
        repo = QuickTapApp.get().repo();
        prefs = AppPrefs.get(application);
        categories = repo.observeCategories();
        products = Transformations.switchMap(filter, key -> {
            String[] parts = key.split("\\|", 2);
            return repo.observeProducts(Long.parseLong(parts[0]), parts.length > 1 ? parts[1] : "");
        });
    }

    public LiveData<List<Product>> products() { return products; }

    public LiveData<List<Category>> categories() { return categories; }

    public LiveData<List<CartLine>> cart() { return cart; }

    public void setCategory(long id) {
        categoryId = id;
        filter.setValue(categoryId + "|" + query);
    }

    public void setQuery(String text) {
        query = text == null ? "" : text.trim();
        filter.setValue(categoryId + "|" + query);
    }

    // ---------------- cart operations ----------------

    public void add(Product product, String categoryName) {
        List<CartLine> lines = current();
        for (CartLine line : lines) {
            if (line.productId == product.id) {
                line.qty++;
                cart.setValue(lines);
                return;
            }
        }
        lines.add(new CartLine(product, categoryName));
        cart.setValue(lines);
    }

    public void setQty(int index, int qty) {
        List<CartLine> lines = current();
        if (index < 0 || index >= lines.size()) return;
        if (qty <= 0) lines.remove(index);
        else lines.get(index).qty = qty;
        cart.setValue(lines);
    }

    public void remove(int index) { setQty(index, 0); }

    public void clear() { cart.setValue(new ArrayList<>()); }

    /** Adds an exact quantity in one shot (long press on a product card). */
    public void addQty(Product product, String categoryName, int qty) {
        if (qty <= 0) return;
        List<CartLine> lines = current();
        for (CartLine line : lines) {
            if (line.productId == product.id) {
                line.qty += qty;
                cart.setValue(lines);
                return;
            }
        }
        CartLine line = new CartLine(product, categoryName);
        line.qty = qty;
        lines.add(line);
        cart.setValue(lines);
    }

    /** Detached copy of the cart, safe to park in storage. */
    public List<CartLine> snapshot() {
        List<CartLine> copy = new ArrayList<>();
        for (CartLine line : current()) copy.add(line.copy());
        return copy;
    }

    /** Replaces the cart with a resumed parked order. */
    public void restore(List<CartLine> lines) {
        List<CartLine> copy = new ArrayList<>();
        if (lines != null) for (CartLine line : lines) copy.add(line.copy());
        cart.setValue(copy);
    }


    private List<CartLine> current() {
        List<CartLine> lines = cart.getValue();
        return lines == null ? new ArrayList<>() : new ArrayList<>(lines);
    }

    // ---------------- totals ----------------

    public double subtotal() {
        double sum = 0;
        for (CartLine line : current()) sum += line.total();
        return sum;
    }

    public double discountAmount(double subtotal) {
        return subtotal * prefs.getDiscountPercent() / 100d;
    }

    public double taxAmount(double taxable) {
        return taxable * prefs.getTaxPercent() / 100d;
    }

    public double grandTotal() {
        double sub = subtotal();
        double discount = discountAmount(sub);
        double taxable = sub - discount;
        return taxable + taxAmount(taxable);
    }

    public interface SaveCallback {
        void onSaved(Bill bill, List<BillItem> items);
    }

    /** Persists the bill off the UI thread and hands it back for printing. */
    public void saveBill(String orderType, String tableNo, String customerName,
                         String customerPhone, String address, String notes,
                         boolean paid, SaveCallback callback) {
        List<CartLine> lines = current();
        if (lines.isEmpty()) return;

        double sub = subtotal();
        double discount = discountAmount(sub);
        double taxable = sub - discount;
        double tax = taxAmount(taxable);

        Bill bill = new Bill();
        bill.createdAt = System.currentTimeMillis();
        bill.orderType = orderType;
        bill.tableNo = tableNo;
        bill.customerName = customerName;
        bill.customerPhone = customerPhone;
        bill.address = address;
        bill.notes = notes;
        bill.subtotal = sub;
        bill.discount = discount;
        bill.tax = tax;
        bill.total = taxable + tax;
        bill.paid = paid;

        List<BillItem> items = new ArrayList<>();
        for (CartLine line : lines) {
            BillItem item = new BillItem();
            item.productId = line.productId;
            item.name = line.name;
            item.categoryName = line.categoryName;
            item.price = line.price;
            item.qty = line.qty;
            items.add(item);
        }

        AppExecutors.io().execute(() -> {
            long id = repo.saveBillBlocking(bill, items);
            bill.id = id;
            AppExecutors.main().post(() -> callback.onSaved(bill, items));
        });
    }
}
