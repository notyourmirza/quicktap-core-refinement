package com.quicktap.pos.ui.plans;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.quicktap.pos.R;
import com.quicktap.pos.theme.RemoteTheme;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/** Renders the plan cards with feature lists and a selected state. */
public class PlanAdapter extends RecyclerView.Adapter<PlanAdapter.Holder> {

    public interface Listener { void onSelect(int position); }

    private List<PlanCatalog.Plan> plans;
    private final Listener listener;
    private final String currency;
    private boolean yearly;
    private int selected;

    public PlanAdapter(List<PlanCatalog.Plan> plans, String currency, int selected, Listener listener) {
        this.plans = plans;
        this.currency = currency;
        this.selected = selected;
        this.listener = listener;
    }

    /** Swaps in the plan list published by the Super Admin. */
    public void submit(List<PlanCatalog.Plan> fresh, int selected) {
        this.plans = fresh;
        this.selected = selected;
        notifyDataSetChanged();
    }

    public void setCycle(boolean yearly) {
        this.yearly = yearly;
        notifyDataSetChanged();
    }

    public void setSelected(int position) {
        int previous = selected;
        selected = position;
        notifyItemChanged(previous);
        notifyItemChanged(position);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_plan, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        PlanCatalog.Plan plan = plans.get(position);
        int accent = RemoteTheme.primary(holder.itemView.getContext());

        holder.name.setText(plan.name);
        holder.tagline.setText(plan.tagline);
        holder.price.setText(currency + " " + format(plan.price(yearly)));
        holder.cycle.setText(yearly ? "/ year" : "/ month");

        if (plan.tag == null) {
            holder.tag.setVisibility(View.GONE);
        } else {
            holder.tag.setVisibility(View.VISIBLE);
            holder.tag.setText(plan.tag);
            holder.tag.setTextColor(accent);
        }

        holder.features.removeAllViews();
        for (String feature : plan.features) holder.features.addView(featureRow(holder, feature, accent));

        boolean isSelected = position == selected;
        holder.card.setChecked(isSelected);
        holder.card.setStrokeWidth(Math.round(holder.density * (isSelected ? 2 : 1)));
        holder.card.setStrokeColor(isSelected ? accent : holder.outline());
        holder.card.setOnClickListener(v -> {
            setSelected(holder.getBindingAdapterPosition());
            if (listener != null) listener.onSelect(holder.getBindingAdapterPosition());
        });
    }

    private View featureRow(Holder holder, String label, int accent) {
        LinearLayout row = new LinearLayout(holder.itemView.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, Math.round(holder.density * 4), 0, Math.round(holder.density * 4));

        ImageView icon = new ImageView(holder.itemView.getContext());
        icon.setImageResource(R.drawable.ic_shield_check);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(accent));
        int size = Math.round(holder.density * 16);
        row.addView(icon, new LinearLayout.LayoutParams(size, size));

        TextView text = new TextView(holder.itemView.getContext());
        text.setText(label);
        text.setTextSize(13f);
        text.setTextColor(RemoteTheme.textPrimary(holder.itemView.getContext()));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMarginStart(Math.round(holder.density * 10));
        row.addView(text, params);
        return row;
    }

    private String format(int amount) {
        return NumberFormat.getNumberInstance(Locale.getDefault()).format(amount);
    }

    @Override
    public int getItemCount() { return plans.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final TextView name, tagline, tag, price, cycle;
        final LinearLayout features;
        final float density;

        Holder(@NonNull View view) {
            super(view);
            card = view.findViewById(R.id.cardPlan);
            name = view.findViewById(R.id.textPlanName);
            tagline = view.findViewById(R.id.textPlanTagline);
            tag = view.findViewById(R.id.textPlanTag);
            price = view.findViewById(R.id.textPlanPrice);
            cycle = view.findViewById(R.id.textPlanCycle);
            features = view.findViewById(R.id.containerFeatures);
            density = view.getResources().getDisplayMetrics().density;
        }

        int outline() { return RemoteTheme.outline(itemView.getContext()); }
    }
}
