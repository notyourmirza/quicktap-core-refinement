package com.quicktap.pos.ui.history;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.quicktap.pos.data.entity.Bill;
import com.quicktap.pos.databinding.ItemBillBinding;
import com.quicktap.pos.util.DateUtil;
import com.quicktap.pos.util.Money;

import java.util.ArrayList;
import java.util.List;

/** Recent bills list with reprint and delete. */
public class BillAdapter extends RecyclerView.Adapter<BillAdapter.VH> {

    public interface Listener {
        void onReprint(long billId);
        void onDelete(long billId);
    }

    private final List<Bill> bills = new ArrayList<>();
    private final Listener listener;
    private final String currency;

    public BillAdapter(String currency, Listener listener) {
        this.currency = currency;
        this.listener = listener;
    }

    public void submit(List<Bill> items) {
        bills.clear();
        if (items != null) bills.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemBillBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Bill bill = bills.get(position);
        ItemBillBinding b = holder.binding;
        b.textInvoice.setText(bill.invoiceNo);
        b.textDate.setText(DateUtil.receipt(bill.createdAt));
        b.textTotal.setText(Money.withCurrency(currency, bill.total));
        b.textStatus.setText(bill.paid ? "PAID" : "UNPAID");
        b.textType.setText(readable(bill.orderType)
                + (bill.tableNo == null || bill.tableNo.isEmpty() ? "" : " · Table " + bill.tableNo));
        b.buttonReprint.setOnClickListener(v -> listener.onReprint(bill.id));
        b.buttonDelete.setOnClickListener(v -> listener.onDelete(bill.id));
    }

    private String readable(String type) {
        switch (type) {
            case Bill.TYPE_TAKE_AWAY: return "Take Away";
            case Bill.TYPE_DELIVERY: return "Delivery";
            default: return "Dine In";
        }
    }

    @Override public int getItemCount() { return bills.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ItemBillBinding binding;
        VH(ItemBillBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
