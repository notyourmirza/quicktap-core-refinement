package com.quicktap.pos.ui.products;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.quicktap.pos.QuickTapApp;
import com.quicktap.pos.data.entity.Category;
import com.quicktap.pos.data.entity.Product;
import com.quicktap.pos.databinding.ActivityProductEditBinding;
import com.quicktap.pos.util.AppExecutors;

import java.util.ArrayList;
import java.util.List;

/** Add or edit a product, including its photo, price, category and stock. */
public class ProductEditActivity extends AppCompatActivity {

    public static final String EXTRA_PRODUCT_ID = "product_id";

    private ActivityProductEditBinding binding;
    private Product product = new Product();
    private final List<Category> categories = new ArrayList<>();
    private ActivityResultLauncher<String> imagePicker;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProductEditBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        imagePicker = registerForActivityResult(
                new ActivityResultContracts.GetContent(), this::onImagePicked);

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.buttonPickImage.setOnClickListener(v -> imagePicker.launch("image/*"));
        binding.buttonSave.setOnClickListener(v -> save());

        long id = getIntent().getLongExtra(EXTRA_PRODUCT_ID, 0);
        loadCategories(id);
    }

    private void onImagePicked(Uri uri) {
        if (uri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Some pickers do not offer a persistable grant; the URI still works this session.
        }
        product.imageUri = uri.toString();
        binding.imagePreview.setImageURI(uri);
    }

    private void loadCategories(long productId) {
        AppExecutors.io().execute(() -> {
            List<Category> loaded = QuickTapApp.get().repo().categoriesBlocking();
            Product loadedProduct = productId == 0
                    ? new Product()
                    : com.quicktap.pos.data.AppDatabase.get(this).productDao().byId(productId);
            AppExecutors.main().post(() -> {
                categories.clear();
                categories.addAll(loaded);
                if (loadedProduct != null) product = loadedProduct;
                bindCategorySpinner();
                bindProduct();
            });
        });
    }

    private void bindCategorySpinner() {
        List<String> names = new ArrayList<>();
        for (Category category : categories) names.add(category.name);
        binding.spinnerCategory.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, names));
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).id == product.categoryId) {
                binding.spinnerCategory.setSelection(i);
                break;
            }
        }
    }

    private void bindProduct() {
        binding.toolbar.setTitle(product.id == 0 ? "Add product" : "Edit product");
        binding.inputName.setText(product.name);
        if (product.price > 0) binding.inputPrice.setText(String.valueOf(product.price));
        binding.inputBarcode.setText(product.barcode == null ? "" : product.barcode);
        binding.inputStock.setText(product.stock < 0 ? "" : String.valueOf(product.stock));
        binding.switchAvailable.setChecked(product.available);
        binding.switchFavorite.setChecked(product.favorite);
        if (product.imageUri != null && !product.imageUri.isEmpty()) {
            binding.imagePreview.setImageURI(Uri.parse(product.imageUri));
        }
    }

    private void save() {
        String name = binding.inputName.getText().toString().trim();
        if (name.isEmpty()) {
            binding.layoutName.setError("Product name is required");
            return;
        }
        binding.layoutName.setError(null);

        double price;
        try {
            price = Double.parseDouble(binding.inputPrice.getText().toString().trim());
        } catch (Exception e) {
            binding.layoutPrice.setError("Enter a price");
            return;
        }
        binding.layoutPrice.setError(null);

        if (categories.isEmpty()) {
            Toast.makeText(this, "Create a category first", Toast.LENGTH_LONG).show();
            return;
        }

        product.name = name;
        product.price = price;
        product.categoryId = categories.get(binding.spinnerCategory.getSelectedItemPosition()).id;
        product.barcode = binding.inputBarcode.getText().toString().trim();
        String stock = binding.inputStock.getText().toString().trim();
        product.stock = stock.isEmpty() ? -1 : Integer.parseInt(stock);
        product.available = binding.switchAvailable.isChecked();
        product.favorite = binding.switchFavorite.isChecked();

        QuickTapApp.get().repo().saveProduct(product, () -> {
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Shared mechanism: banner + server-authoritative licence gate.
        com.quicktap.pos.ui.license.LicenseGuard.protect(this);
    }
}
