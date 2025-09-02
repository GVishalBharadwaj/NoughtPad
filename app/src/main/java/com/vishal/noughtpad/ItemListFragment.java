package com.vishal.noughtpad;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ItemListFragment extends Fragment implements ReminderAdapter.OnItemClickListener {

    private static final String ARG_CATEGORIES = "ARG_CATEGORIES";
    private ReminderViewModel reminderViewModel;
    private ReminderAdapter adapter;
    private ArrayList<String> categoriesToShow;

    public static ItemListFragment newInstance(ArrayList<String> categories) {
        ItemListFragment fragment = new ItemListFragment();
        Bundle args = new Bundle();
        args.putStringArrayList(ARG_CATEGORIES, categories);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            categoriesToShow = getArguments().getStringArrayList(ARG_CATEGORIES);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        boolean isExpenseTab = categoriesToShow != null && categoriesToShow.contains("RECEIPT");
        if (isExpenseTab) {
            return inflater.inflate(R.layout.fragment_expense_list, container, false);
        } else {
            return inflater.inflate(R.layout.fragment_item_list, container, false);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView recyclerView = view.findViewById(R.id.recycler_view_fragment);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ReminderAdapter();
        adapter.setOnItemClickListener(this);
        recyclerView.setAdapter(adapter);

        reminderViewModel = new ViewModelProvider(requireActivity()).get(ReminderViewModel.class);

        // This single observer is our "source of truth" for all UI updates
        reminderViewModel.getAllItems().observe(getViewLifecycleOwner(), allItems -> {
            if (allItems == null) return;

            // --- 1. Filter the list for the RecyclerView ---
            List<ReminderItem> filteredList = new ArrayList<>();
            if (categoriesToShow.contains("ALL_REMINDERS")) {
                for (ReminderItem item : allItems) {
                    if ("BILL".equals(item.category) || "TICKET".equals(item.category) || "TASK".equals(item.category)) {
                        filteredList.add(item);
                    }
                }
            } else {
                String category = categoriesToShow.get(0);
                for (ReminderItem item : allItems) {
                    if (category.equals(item.category)) {
                        filteredList.add(item);
                    }
                }
            }
            adapter.submitList(filteredList);

            // --- 2. If this is the Expenses tab, perform the calculations ---
            boolean isExpenseTab = categoriesToShow != null && categoriesToShow.contains("RECEIPT");
            if (isExpenseTab) {
                calculateAndDisplayExpenses(view, allItems);
            }
        });
    }

    private void calculateAndDisplayExpenses(View view, List<ReminderItem> allItems) {
        final TextView tvToday = view.findViewById(R.id.text_total_today);
        final TextView tvWeek = view.findViewById(R.id.text_total_week);
        final TextView tvMonth = view.findViewById(R.id.text_total_month);
        final TextView tvAllTime = view.findViewById(R.id.text_total_all_time);

        double totalToday = 0;
        double totalWeek = 0;
        double totalMonth = 0;
        double totalAllTime = 0;

        long startOfDay = getStartOfDay();
        long startOfWeek = getStartOfWeek();
        long startOfMonth = getStartOfMonth();

        for (ReminderItem item : allItems) {
            if ("RECEIPT".equals(item.category)) {
                totalAllTime += item.amount;
                if (item.reminderTime >= startOfDay) totalToday += item.amount;
                if (item.reminderTime >= startOfWeek) totalWeek += item.amount;
                if (item.reminderTime >= startOfMonth) totalMonth += item.amount;
            }
        }

        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        tvToday.setText(currencyFormat.format(totalToday));
        tvWeek.setText(currencyFormat.format(totalWeek));
        tvMonth.setText(currencyFormat.format(totalMonth));
        tvAllTime.setText(currencyFormat.format(totalAllTime));
    }

    // --- Helper methods to get timestamps ---
    private long getStartOfDay() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
    private long getStartOfWeek() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_WEEK, calendar.getFirstDayOfWeek());
        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
    private long getStartOfMonth() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    @Override
    public void onItemClick(ReminderItem item) {
        Intent intent = new Intent(getActivity(), DetailActivity.class);
        intent.putExtra(MainActivity.EXTRA_ID, item.id);
        startActivity(intent);
    }
}