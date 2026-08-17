package com.quicktap.pos.ui.products;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.quicktap.pos.QuickTapApp;
import com.quicktap.pos.data.entity.Category;
import com.quicktap.pos.data.entity.Product;
import com.quicktap.pos.databinding.FragmentProductsBinding;
import com.quicktap.pos.util.AppPrefs;

import java.util.List;

/** Admin catalogue: products and categories. */
public class ProductsFragment extends Fragment {

    private FragmentProductsBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProductsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ProductManageAdapter adapter = new ProductManageAdapter(
                AppPrefs.get(requireContext()).getCurrency(), new ProductManageAdapter.Listener() {
            @Override public void onEdit(Product product) { openEditor(product.id); }
            @Override public void onDelete(Product product) { confirmDelete(product); }
            @Override public void onToggleAvailable(Product product) {
                QuickTapApp.get().repo().saveProduct(product, null);
            }
        });
        binding.recyclerProducts.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerProducts.setAdapter(adapter);

        QuickTapApp.get().repo().observeAllProducts()
                .observe(getViewLifecycleOwner(), products -> {
                    adapter.submit(products);
                    binding.textEmpty.setVisibility(
                            products == null || products.isEmpty() ? View.VISIBLE : View.GONE);
                });

        QuickTapApp.get().repo().observeCategories()
                .observe(getViewLifecycleOwner(), this::renderCategories);

        binding.fabAddProduct.setOnClickListener(v -> openEditor(0));
        binding.buttonAddCategory.setOnClickListener(v -> editCategory(new Category("", 0)));
    }

    private void renderCategories(List<Category> categories) {
        binding.chipGroupCategories.removeAllViews();
        if (categories == null) return;
        for (Category category : categories) {
            com.google.android.material.chip.Chip chip =
                    new com.google.android.material.chip.Chip(requireContext());
            chip.setText(category.name);
            chip.setCloseIconVisible(true);
            chip.setOnCloseIconClickListener(v ->
                    QuickTapApp.get().repo().deleteCategory(category));
            chip.setOnClickListener(v -> editCategory(category));
            binding.chipGroupCategories.addView(chip);
        }
    }

    private void editCategory(Category category) {
        EditText input = new EditText(requireContext());
        input.setHint("Category name");
        input.setText(category.name);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(category.id == 0 ? "New category" : "Rename category")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    category.name = name;
                    QuickTapApp.get().repo().saveCategory(category);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDelete(Product product) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete " + product.name + "?")
                .setPositiveButton("Delete", (d, w) ->
                        QuickTapApp.get().repo().deleteProduct(product))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openEditor(long productId) {
        Intent intent = new Intent(requireContext(), ProductEditActivity.class);
        intent.putExtra(ProductEditActivity.EXTRA_PRODUCT_ID, productId);
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
