package com.quicktap.pos.ui.products;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.quicktap.pos.data.entity.Product;
import com.quicktap.pos.databinding.ItemProductManageBinding;
import com.quicktap.pos.util.Money;

import java.util.ArrayList;
import java.util.List;

public class ProductManageAdapter extends RecyclerView.Adapter<ProductManageAdapter.VH> {

    public interface Listener {
        void onEdit(Product product);
        void onDelete(Product product);
        void onToggleAvailable(Product product);
    }

    private final List<Product> items = new ArrayList<>();
    private final Listener listener;
    private final String currency;

    public ProductManageAdapter(String currency, Listener listener) {
        this.currency = currency;
        this.listener = listener;
    }

    public void submit(List<Product> products) {
        items.clear();
        if (products != null) items.addAll(products);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemProductManageBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Product product = items.get(position);
        ItemProductManageBinding b = holder.binding;
        b.textName.setText(product.name);
        b.textPrice.setText(Money.withCurrency(currency, product.price));
        b.textStock.setText(product.stock < 0 ? "Stock not tracked" : "Stock: " + product.stock);
        b.switchAvailable.setOnCheckedChangeListener(null);
        b.switchAvailable.setChecked(product.available);
        b.switchAvailable.setOnCheckedChangeListener((v, checked) -> {
            product.available = checked;
            listener.onToggleAvailable(product);
        });
        b.getRoot().setOnClickListener(v -> listener.onEdit(product));
        b.buttonDelete.setOnClickListener(v -> listener.onDelete(product));
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ItemProductManageBinding binding;
        VH(ItemProductManageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
