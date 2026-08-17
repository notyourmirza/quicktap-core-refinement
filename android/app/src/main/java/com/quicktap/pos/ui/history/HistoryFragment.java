package com.quicktap.pos.ui.history;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.quicktap.pos.QuickTapApp;
import com.quicktap.pos.databinding.FragmentHistoryBinding;
import com.quicktap.pos.print.PrintJobs;
import com.quicktap.pos.util.AppPrefs;

/** Full bill history: open, reprint, delete. */
public class HistoryFragment extends Fragment {

    private FragmentHistoryBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHistoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        BillAdapter adapter = new BillAdapter(
                AppPrefs.get(requireContext()).getCurrency(), new BillAdapter.Listener() {
            @Override public void onReprint(long billId) {
                PrintJobs.reprint(requireContext(), billId);
            }
            @Override public void onDelete(long billId) { confirmDelete(billId); }
        });
        binding.recyclerBills.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerBills.setAdapter(adapter);

        QuickTapApp.get().repo().observeRecentBills(300)
                .observe(getViewLifecycleOwner(), bills -> {
                    adapter.submit(bills);
                    binding.textEmpty.setVisibility(
                            bills == null || bills.isEmpty() ? View.VISIBLE : View.GONE);
                });

        binding.buttonReprintLast.setOnClickListener(v -> PrintJobs.reprintLast(requireContext()));
    }

    private void confirmDelete(long billId) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete bill?")
                .setMessage("This removes the bill and its items from reports.")
                .setPositiveButton("Delete", (d, w) ->
                        QuickTapApp.get().repo().deleteBill(billId))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
