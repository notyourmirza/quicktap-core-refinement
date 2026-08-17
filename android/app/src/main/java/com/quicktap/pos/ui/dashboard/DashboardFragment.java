package com.quicktap.pos.ui.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.quicktap.pos.QuickTapApp;
import com.quicktap.pos.databinding.FragmentDashboardBinding;
import com.quicktap.pos.print.PrintJobs;
import com.quicktap.pos.ui.MainActivity;
import com.quicktap.pos.ui.billing.BillingFragment;
import com.quicktap.pos.ui.history.BillAdapter;
import com.quicktap.pos.ui.reports.ReportsFragment;
import com.quicktap.pos.util.AppPrefs;
import com.quicktap.pos.util.DateUtil;
import com.quicktap.pos.util.Money;

/** Today's numbers, recent bills and quick actions. */
public class DashboardFragment extends Fragment {

    private FragmentDashboardBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        AppPrefs prefs = AppPrefs.get(requireContext());
        String currency = prefs.getCurrency();
        binding.textToday.setText(DateUtil.header(System.currentTimeMillis()));

        BillAdapter adapter = new BillAdapter(currency, new BillAdapter.Listener() {
            @Override public void onReprint(long billId) {
                PrintJobs.reprint(requireContext(), billId);
            }
            @Override public void onDelete(long billId) {
                QuickTapApp.get().repo().deleteBill(billId);
            }
        });
        binding.recyclerRecent.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerRecent.setNestedScrollingEnabled(false);
        binding.recyclerRecent.setAdapter(adapter);

        QuickTapApp.get().repo()
                .observeSummary(DateUtil.startOfToday(), DateUtil.endOfToday())
                .observe(getViewLifecycleOwner(), summary -> {
                    if (summary == null) return;
                    binding.textSales.setText(Money.withCurrency(currency, summary.revenue));
                    binding.textOrders.setText(String.valueOf(summary.orders));
                    binding.textPaid.setText(String.valueOf(summary.paidCount));
                    binding.textUnpaid.setText(String.valueOf(summary.unpaidCount));
                    binding.textUnpaidAmount.setText(
                            "Outstanding " + Money.withCurrency(currency, summary.unpaidAmount));
                });

        QuickTapApp.get().repo().observeRecentBills(8)
                .observe(getViewLifecycleOwner(), bills -> {
                    adapter.submit(bills);
                    binding.textNoBills.setVisibility(
                            bills == null || bills.isEmpty() ? View.VISIBLE : View.GONE);
                });

        binding.actionNewBill.setOnClickListener(v ->
                ((MainActivity) requireActivity()).show(new BillingFragment(), false));
        binding.actionReports.setOnClickListener(v ->
                ((MainActivity) requireActivity()).show(new ReportsFragment(), true));
        binding.actionReprint.setOnClickListener(v -> PrintJobs.reprintLast(requireContext()));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
