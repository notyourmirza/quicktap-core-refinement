package com.quicktap.pos.ui.billing;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.quicktap.pos.data.entity.Product;
import com.quicktap.pos.databinding.ItemProductBinding;
import com.quicktap.pos.util.Money;

import java.util.ArrayList;
import java.util.List;

/** Tap = add to bill, long press = edit quantity. */
public class ProductGridAdapter extends RecyclerView.Adapter<ProductGridAdapter.VH> {

    public interface Listener {
        void onTap(Product product);
        void onLongPress(Product product);
    }

    private final List<Product> items = new ArrayList<>();
    private final Listener listener;
    private final String currency;

    public ProductGridAdapter(String currency, Listener listener) {
        this.currency = currency;
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submit(List<Product> products) {
        items.clear();
        if (products != null) items.addAll(products);
        notifyDataSetChanged();
    }

    @Override public long getItemId(int position) { return items.get(position).id; }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemProductBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Product product = items.get(position);
        ItemProductBinding b = holder.binding;

        b.textName.setText(product.name);
        b.textPrice.setText(Money.withCurrency(currency, product.price));
        b.badgeFavorite.setVisibility(product.favorite ? View.VISIBLE : View.GONE);

        boolean soldOut = !product.available || product.stock == 0;
        b.textUnavailable.setVisibility(soldOut ? View.VISIBLE : View.GONE);
        b.getRoot().setAlpha(soldOut ? 0.45f : 1f);
        b.getRoot().setEnabled(!soldOut);

        if (product.imageUri != null && !product.imageUri.isEmpty()) {
            b.imageProduct.setImageURI(Uri.parse(product.imageUri));
        } else {
            b.imageProduct.setImageDrawable(null);
        }
        b.textInitial.setVisibility(
                product.imageUri == null || product.imageUri.isEmpty() ? View.VISIBLE : View.GONE);
        b.textInitial.setText(product.name.isEmpty()
                ? "?" : product.name.substring(0, 1).toUpperCase());

        b.getRoot().setOnClickListener(v -> { if (!soldOut) listener.onTap(product); });
        b.getRoot().setOnLongClickListener(v -> {
            listener.onLongPress(product);
            return true;
        });
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ItemProductBinding binding;
        VH(ItemProductBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
