package com.quicktap.pos.ui.billing;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.quicktap.pos.data.model.CartLine;
import com.quicktap.pos.databinding.ItemCartBinding;
import com.quicktap.pos.util.Money;

import java.util.ArrayList;
import java.util.List;

/** Live order summary rows with +, - and delete. */
public class CartAdapter extends RecyclerView.Adapter<CartAdapter.VH> {

    public interface Listener {
        void onQtyChanged(int index, int qty);
        void onRemove(int index);
    }

    private final List<CartLine> lines = new ArrayList<>();
    private final Listener listener;
    private final String currency;

    public CartAdapter(String currency, Listener listener) {
        this.currency = currency;
        this.listener = listener;
    }

    public void submit(List<CartLine> items) {
        lines.clear();
        if (items != null) lines.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemCartBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        CartLine line = lines.get(position);
        ItemCartBinding b = holder.binding;
        b.textName.setText(line.name);
        b.textPrice.setText(Money.withCurrency(currency, line.price));
        b.textQty.setText(String.valueOf(line.qty));
        b.textTotal.setText(Money.format(line.total()));

        b.buttonPlus.setOnClickListener(v ->
                listener.onQtyChanged(holder.getBindingAdapterPosition(), line.qty + 1));
        b.buttonMinus.setOnClickListener(v ->
                listener.onQtyChanged(holder.getBindingAdapterPosition(), line.qty - 1));
        b.buttonDelete.setOnClickListener(v ->
                listener.onRemove(holder.getBindingAdapterPosition()));
    }

    @Override public int getItemCount() { return lines.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ItemCartBinding binding;
        VH(ItemCartBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
