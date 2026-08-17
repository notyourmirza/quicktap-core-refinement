package com.quicktap.pos.ui.billing;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.quicktap.pos.QuickTapApp;
import com.quicktap.pos.R;
import com.quicktap.pos.data.entity.Bill;
import com.quicktap.pos.data.entity.Category;
import com.quicktap.pos.data.entity.Product;
import com.quicktap.pos.data.model.CartLine;
import com.quicktap.pos.databinding.FragmentBillingBinding;
import com.quicktap.pos.databinding.SheetCheckoutBinding;
import com.quicktap.pos.databinding.SheetHeldOrdersBinding;
import com.quicktap.pos.print.PrintJobs;
import com.quicktap.pos.util.AppPrefs;
import com.quicktap.pos.util.HeldOrders;
import com.quicktap.pos.util.Money;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pick products from a full-height grid, then checkout inside a bottom sheet. */
public class BillingFragment extends Fragment {

    private FragmentBillingBinding binding;
    private SheetCheckoutBinding sheetBinding;
    private BottomSheetDialog sheet;
    private BillingViewModel viewModel;
    private ProductGridAdapter productAdapter;
    private CartAdapter cartAdapter;
    private AppPrefs prefs;
    private HeldOrders heldOrders;

    private final Map<Long, String> categoryNames = new HashMap<>();
    private final List<Product> visibleProducts = new ArrayList<>();
    private String orderType = Bill.TYPE_DINE_IN;
    private boolean paid = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentBillingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        prefs = AppPrefs.get(requireContext());
        heldOrders = HeldOrders.get(requireContext());
        viewModel = new ViewModelProvider(this).get(BillingViewModel.class);

        setupProductGrid();
        setupSearch();
        binding.buttonCheckout.setOnClickListener(v -> openCheckout());
        binding.buttonParked.setOnClickListener(v -> openHeldOrders());
        observe();
        refreshParkedButton();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshParkedButton();
    }

    private void setupProductGrid() {
        int widthDp = getResources().getConfiguration().screenWidthDp;
        int columns = widthDp >= 600 ? 4 : 3;
        productAdapter = new ProductGridAdapter(prefs.getCurrency(), new ProductGridAdapter.Listener() {
            @Override public void onTap(Product product) {
                viewModel.add(product, categoryNames.get(product.categoryId));
            }
            @Override public void onLongPress(Product product) {
                askQuantity(product);
            }
        });
        binding.recyclerProducts.setLayoutManager(new GridLayoutManager(requireContext(), columns));
        binding.recyclerProducts.setAdapter(productAdapter);
    }

    /**
     * Typing filters the grid live. Pressing enter — which is exactly what a USB
     * or Bluetooth barcode scanner sends after the code — adds the matching
     * product straight to the cart and clears the field for the next scan.
     */
    private void setupSearch() {
        binding.inputSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                viewModel.setQuery(s.toString());
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        binding.inputSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                quickAdd(binding.inputSearch.getText() == null
                        ? "" : binding.inputSearch.getText().toString().trim());
                return true;
            }
            return false;
        });
    }

    /** Scanner / enter key: exact barcode wins, otherwise the only visible match. */
    private void quickAdd(String code) {
        if (code.isEmpty()) return;
        Product match = null;
        for (Product product : visibleProducts) {
            if (code.equalsIgnoreCase(product.barcode)) { match = product; break; }
        }
        if (match == null && visibleProducts.size() == 1) match = visibleProducts.get(0);
        if (match == null) {
            Toast.makeText(requireContext(), "No product for \"" + code + "\"",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        viewModel.add(match, categoryNames.get(match.categoryId));
        binding.inputSearch.setText("");
    }

    // ---------------- checkout sheet ----------------

    private void openCheckout() {
        if (sheet == null) buildSheet();
        renderCart(viewModel.cart().getValue());
        sheet.show();
    }

    private void buildSheet() {
        sheetBinding = SheetCheckoutBinding.inflate(getLayoutInflater());
        sheet = new BottomSheetDialog(requireContext());
        sheet.setContentView(sheetBinding.getRoot());
        sheet.getBehavior().setSkipCollapsed(true);
        sheet.getBehavior().setState(com.google.android.material.bottomsheet
                .BottomSheetBehavior.STATE_EXPANDED);
        sizeSheet();

        cartAdapter = new CartAdapter(prefs.getCurrency(), new CartAdapter.Listener() {
            @Override public void onQtyChanged(int index, int qty) { viewModel.setQty(index, qty); }
            @Override public void onRemove(int index) { viewModel.remove(index); }
        });
        sheetBinding.recyclerCart.setLayoutManager(new LinearLayoutManager(requireContext()));
        sheetBinding.recyclerCart.setNestedScrollingEnabled(false);
        sheetBinding.recyclerCart.setAdapter(cartAdapter);
        attachSwipeToDelete(sheetBinding.recyclerCart);

        sheetBinding.buttonClearCart.setOnClickListener(v -> viewModel.clear());
        sheetBinding.buttonPrint.setOnClickListener(v -> printAndReset());
        sheetBinding.buttonHold.setOnClickListener(v -> askHoldLabel());
        sheetBinding.chipGroupPayment.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            applyPaymentChoice(ids.get(0));
        });
        applyPaymentChoice(R.id.chipPaid);
    }

    /** Swipe a cart row away, with an undo window so a mis-swipe costs nothing. */
    private void attachSwipeToDelete(RecyclerView recycler) {
        final Paint paint = new Paint();
        paint.setColor(Color.parseColor("#33F44336"));
        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
                int index = holder.getBindingAdapterPosition();
                List<CartLine> before = viewModel.snapshot();
                viewModel.remove(index);
                Snackbar.make(sheetBinding.getRoot(), R.string.item_removed, Snackbar.LENGTH_SHORT)
                        .setAction(R.string.undo, v -> viewModel.restore(before))
                        .show();
            }

            @Override
            public void onChildDraw(@NonNull Canvas canvas, @NonNull RecyclerView rv,
                                    @NonNull RecyclerView.ViewHolder holder, float dX, float dY,
                                    int actionState, boolean isActive) {
                View item = holder.itemView;
                RectF area = new RectF(item.getLeft(), item.getTop(), item.getRight(), item.getBottom());
                canvas.drawRoundRect(area, 18f, 18f, paint);
                super.onChildDraw(canvas, rv, holder, dX, dY, actionState, isActive);
            }
        });
        helper.attachToRecyclerView(recycler);
    }

    /** Gives the sheet a tall, fixed frame so the cart scrolls instead of collapsing. */
    private void sizeSheet() {
        View container = sheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (container == null) return;
        int height = Math.round(getResources().getDisplayMetrics().heightPixels * 0.90f);
        ViewGroup.LayoutParams params = container.getLayoutParams();
        params.height = height;
        container.setLayoutParams(params);
        sheet.getBehavior().setPeekHeight(height);
    }

    /** One chosen type drives both payment status and order type. */
    private void applyPaymentChoice(int checkedId) {
        if (checkedId == R.id.chipCod) {
            paid = false;
            orderType = Bill.TYPE_DELIVERY;
        } else if (checkedId == R.id.chipPaid) {
            paid = true;
            orderType = Bill.TYPE_TAKE_AWAY;
        } else if (checkedId == R.id.chipDineIn) {
            paid = true;
            orderType = Bill.TYPE_DINE_IN;
        } else if (checkedId == R.id.chipTakeAway) {
            paid = true;
            orderType = Bill.TYPE_TAKE_AWAY;
        } else if (checkedId == R.id.chipDelivery) {
            paid = false;
            orderType = Bill.TYPE_DELIVERY;
        }
    }

    private void observe() {
        viewModel.categories().observe(getViewLifecycleOwner(), this::renderCategories);
        viewModel.products().observe(getViewLifecycleOwner(), products -> {
            visibleProducts.clear();
            if (products != null) visibleProducts.addAll(products);
            productAdapter.submit(products);
        });
        viewModel.cart().observe(getViewLifecycleOwner(), this::renderCart);
    }

    private void renderCart(List<CartLine> lines) {
        boolean empty = lines == null || lines.isEmpty();
        int count = 0;
        if (lines != null) for (CartLine line : lines) count += line.qty;

        binding.cardCheckoutBar.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.textBarCount.setText(count + (count == 1 ? " item" : " items"));
        binding.textBarTotal.setText(Money.withCurrency(prefs.getCurrency(), viewModel.grandTotal()));

        if (sheetBinding == null) return;
        cartAdapter.submit(lines);
        sheetBinding.textSheetCount.setText(count + (count == 1 ? " item" : " items")
                + " · " + Money.withCurrency(prefs.getCurrency(), viewModel.grandTotal()));
        sheetBinding.recyclerCart.setVisibility(empty ? View.GONE : View.VISIBLE);
        sheetBinding.textCartEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        sheetBinding.buttonPrint.setEnabled(!empty);
        sheetBinding.buttonHold.setEnabled(!empty);
        renderTotals();
        if (empty && sheet != null && sheet.isShowing()) sheet.dismiss();
    }

    // ---------------- parked bills ----------------

    private void refreshParkedButton() {
        if (binding == null) return;
        int count = heldOrders.count();
        binding.buttonParked.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
        binding.buttonParked.setText(getString(R.string.held_short) + " · " + count);
    }

    private void askHoldLabel() {
        EditText input = new EditText(requireContext());
        input.setHint(R.string.held_name_hint);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        String typed = text(sheetBinding.inputCustomerName);
        if (!typed.isEmpty()) input.setText(typed);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.hold_bill)
                .setView(input)
                .setPositiveButton(R.string.hold_bill, (d, w) -> {
                    heldOrders.hold(input.getText().toString(), viewModel.snapshot());
                    Toast.makeText(requireContext(), R.string.held_saved, Toast.LENGTH_SHORT).show();
                    resetForNextCustomer();
                    refreshParkedButton();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openHeldOrders() {
        SheetHeldOrdersBinding heldBinding = SheetHeldOrdersBinding.inflate(getLayoutInflater());
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        dialog.setContentView(heldBinding.getRoot());

        HeldOrdersAdapter[] holder = new HeldOrdersAdapter[1];
        holder[0] = new HeldOrdersAdapter(prefs.getCurrency(), new HeldOrdersAdapter.Listener() {
            @Override public void onResume(HeldOrders.Held held) {
                viewModel.restore(held.lines);
                heldOrders.remove(held.id);
                refreshParkedButton();
                dialog.dismiss();
                openCheckout();
            }

            @Override public void onDelete(HeldOrders.Held held) {
                heldOrders.remove(held.id);
                List<HeldOrders.Held> left = heldOrders.all();
                holder[0].submit(left);
                heldBinding.textHeldEmpty.setVisibility(left.isEmpty() ? View.VISIBLE : View.GONE);
                refreshParkedButton();
                if (left.isEmpty()) dialog.dismiss();
            }
        });
        heldBinding.recyclerHeld.setLayoutManager(new LinearLayoutManager(requireContext()));
        heldBinding.recyclerHeld.setAdapter(holder[0]);

        List<HeldOrders.Held> orders = heldOrders.all();
        holder[0].submit(orders);
        heldBinding.textHeldEmpty.setVisibility(orders.isEmpty() ? View.VISIBLE : View.GONE);
        heldBinding.buttonClearHeld.setOnClickListener(v -> {
            heldOrders.clearAll();
            refreshParkedButton();
            dialog.dismiss();
        });
        dialog.show();
    }

    private void renderCategories(List<Category> categories) {
        categoryNames.clear();
        binding.chipGroupCategories.removeAllViews();
        addChip("All", 0, true);
        if (categories == null) return;
        for (Category category : categories) {
            categoryNames.put(category.id, category.name);
            addChip(category.name, category.id, false);
        }
    }

    private void addChip(String label, long id, boolean checked) {
        Chip chip = new Chip(requireContext());
        chip.setText(label);
        chip.setCheckable(true);
        chip.setChecked(checked);
        chip.setOnClickListener(v -> {
            for (int i = 0; i < binding.chipGroupCategories.getChildCount(); i++) {
                View child = binding.chipGroupCategories.getChildAt(i);
                if (child instanceof Chip) ((Chip) child).setChecked(child == chip);
            }
            viewModel.setCategory(id);
        });
        binding.chipGroupCategories.addView(chip);
    }

    private void renderTotals() {
        if (sheetBinding == null) return;
        String currency = prefs.getCurrency();
        double sub = viewModel.subtotal();
        double discount = viewModel.discountAmount(sub);
        double tax = viewModel.taxAmount(sub - discount);
        sheetBinding.textSubtotal.setText(Money.withCurrency(currency, sub));
        sheetBinding.textDiscount.setText("-" + Money.withCurrency(currency, discount));
        sheetBinding.textTax.setText(Money.withCurrency(currency, tax));
        sheetBinding.textGrandTotal.setText(Money.withCurrency(currency, viewModel.grandTotal()));
    }

    /** Long press on a product card: type an exact quantity or pin it as a favourite. */
    private void askQuantity(Product product) {
        EditText input = new EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("Quantity");
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(product.name)
                .setView(input)
                .setPositiveButton("Add", (d, w) -> {
                    int qty = parseQty(input.getText() == null ? "" : input.getText().toString());
                    if (qty > 0) viewModel.addQty(product, categoryNames.get(product.categoryId), qty);
                })
                .setNeutralButton(R.string.mark_favourite, (d, w) -> {
                    product.favorite = !product.favorite;
                    QuickTapApp.get().repo().saveProduct(product, () -> { });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private int parseQty(String text) {
        try { return Integer.parseInt(text.trim()); } catch (Exception e) { return 0; }
    }

    /** Save, print, clear, ready for the next customer. No confirmation dialogs. */
    private void printAndReset() {
        sheetBinding.buttonPrint.setEnabled(false);
        viewModel.saveBill(
                orderType,
                "",
                text(sheetBinding.inputCustomerName),
                "",
                "",
                "",
                paid,
                (bill, items) -> {
                    PrintJobs.print(requireContext(), bill, items);
                    Toast.makeText(requireContext(),
                            "Bill " + bill.invoiceNo + " saved", Toast.LENGTH_SHORT).show();
                    resetForNextCustomer();
                });
    }

    private void resetForNextCustomer() {
        viewModel.clear();
        if (sheetBinding != null) {
            sheetBinding.inputCustomerName.setText("");
            sheetBinding.chipGroupPayment.check(R.id.chipPaid);
            sheetBinding.buttonPrint.setEnabled(true);
        }
        binding.inputSearch.setText("");
        if (sheet != null && sheet.isShowing()) sheet.dismiss();
        binding.recyclerProducts.smoothScrollToPosition(0);
    }

    private String text(EditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (sheet != null && sheet.isShowing()) sheet.dismiss();
        sheet = null;
        sheetBinding = null;
        binding = null;
    }
}
