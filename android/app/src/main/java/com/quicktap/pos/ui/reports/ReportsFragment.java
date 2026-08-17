package com.quicktap.pos.ui.reports;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.quicktap.pos.QuickTapApp;
import com.quicktap.pos.data.model.CategorySales;
import com.quicktap.pos.data.model.DaySummary;
import com.quicktap.pos.data.model.ProductSales;
import com.quicktap.pos.databinding.FragmentReportsBinding;
import com.quicktap.pos.util.AppExecutors;
import com.quicktap.pos.util.AppPrefs;
import com.quicktap.pos.util.CsvExport;
import com.quicktap.pos.util.DateUtil;
import com.quicktap.pos.util.Money;

import java.util.ArrayList;
import java.util.List;

/** Today / week / month reports with sales by product and category, plus CSV export. */
public class ReportsFragment extends Fragment {

    private FragmentReportsBinding binding;
    private ActivityResultLauncher<String> exportPicker;

    private long from = DateUtil.startOfToday();
    private long to = DateUtil.endOfToday();
    private String title = "Today's report";

    private final List<ProductSales> productRows = new ArrayList<>();
    private final List<CategorySales> categoryRows = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentReportsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        exportPicker = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("text/csv"), this::writeExport);

        binding.chipRange.check(binding.chipToday.getId());
        binding.chipToday.setOnClickListener(v ->
                load(DateUtil.startOfToday(), DateUtil.endOfToday(), "Today's report"));
        binding.chipWeek.setOnClickListener(v ->
                load(DateUtil.startOfWeek(), DateUtil.endOfToday(), "Weekly report"));
        binding.chipMonth.setOnClickListener(v ->
                load(DateUtil.startOfMonth(), DateUtil.endOfToday(), "Monthly report"));
        binding.buttonExport.setOnClickListener(v ->
                exportPicker.launch("quicktap_report_"
                        + DateUtil.fileStamp(System.currentTimeMillis()) + ".csv"));

        load(from, to, title);
    }

    private void load(long start, long end, String reportTitle) {
        from = start;
        to = end;
        title = reportTitle;
        binding.textTitle.setText(reportTitle);

        AppExecutors.io().execute(() -> {
            DaySummary summary = QuickTapApp.get().repo().summary(start, end);
            List<ProductSales> products = QuickTapApp.get().repo().salesByProduct(start, end);
            List<CategorySales> categories = QuickTapApp.get().repo().salesByCategory(start, end);
            AppExecutors.main().post(() -> {
                if (binding == null) return;
                productRows.clear();
                productRows.addAll(products);
                categoryRows.clear();
                categoryRows.addAll(categories);
                render(summary, products, categories);
            });
        });
    }

    private void render(DaySummary summary, List<ProductSales> products,
                        List<CategorySales> categories) {
        String currency = AppPrefs.get(requireContext()).getCurrency();
        binding.textRevenue.setText(Money.withCurrency(currency,
                summary == null ? 0 : summary.revenue));
        binding.textOrders.setText(String.valueOf(summary == null ? 0 : summary.orders));
        binding.textUnpaid.setText(Money.withCurrency(currency,
                summary == null ? 0 : summary.unpaidAmount));

        binding.containerProducts.removeAllViews();
        if (products.isEmpty()) {
            binding.containerProducts.addView(empty("No product sales in this period"));
        } else {
            for (ProductSales row : products) {
                binding.containerProducts.addView(
                        row(row.name + "  x" + row.qty, Money.withCurrency(currency, row.amount)));
            }
        }
        binding.containerCategories.removeAllViews();
        if (categories.isEmpty()) {
            binding.containerCategories.addView(empty("No category sales in this period"));
        } else {
            for (CategorySales row : categories) {
                binding.containerCategories.addView(
                        row(row.category + "  x" + row.qty, Money.withCurrency(currency, row.amount)));
            }
        }
    }

    /** Placeholder row so an empty range never looks like a broken report. */
    private View empty(String message) {
        TextView text = new TextView(requireContext());
        text.setText(message);
        text.setTextSize(13f);
        text.setTextColor(com.quicktap.pos.theme.RemoteTheme.textMuted(requireContext()));
        int pad = Math.round(getResources().getDisplayMetrics().density * 10);
        text.setPadding(0, pad, 0, pad);
        return text;
    }

    /** Reports must reflect bills taken since the tab was last opened. */
    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) load(from, to, title);
    }

    private View row(String left, String right) {
        View view = LayoutInflater.from(requireContext())
                .inflate(com.quicktap.pos.R.layout.item_report_row, binding.containerProducts, false);
        ((TextView) view.findViewById(com.quicktap.pos.R.id.textLeft)).setText(left);
        ((TextView) view.findViewById(com.quicktap.pos.R.id.textRight)).setText(right);
        return view;
    }

    private void writeExport(Uri uri) {
        if (uri == null) return;
        AppExecutors.io().execute(() -> {
            try {
                CsvExport.writeReport(requireContext(), uri, title, productRows, categoryRows);
                AppExecutors.main().post(() -> toast("Report exported"));
            } catch (Exception e) {
                AppExecutors.main().post(() -> toast("Export failed: " + e.getMessage()));
            }
        });
    }

    private void toast(String message) {
        if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
