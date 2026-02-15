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
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
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
            if (allItems == null)
                return;

            // --- 1. Filter the list for the RecyclerView ---
            List<ReminderItem> filteredList = new ArrayList<>();
            if (categoriesToShow.contains("ALL_REMINDERS")) {
                for (ReminderItem item : allItems) {
                    if ("BILL".equals(item.category) || "TICKET".equals(item.category)
                            || "TASK".equals(item.category)) {
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
        final com.github.mikephil.charting.charts.PieChart pieChart = view.findViewById(R.id.pieChart);

        double totalToday = 0;
        double totalWeek = 0;
        double totalMonth = 0;
        double totalAllTime = 0;

        long startOfDay = getStartOfDay();
        long startOfWeek = getStartOfWeek();
        long startOfMonth = getStartOfMonth();

        // Aggregation for Chart
        java.util.Map<String, Double> categoryTotals = new java.util.HashMap<>();

        for (ReminderItem item : allItems) {
            if ("RECEIPT".equals(item.category)) {
                double amt = item.amount;
                totalAllTime += amt;
                if (item.reminderTime >= startOfDay)
                    totalToday += amt;
                if (item.reminderTime >= startOfWeek)
                    totalWeek += amt;
                if (item.reminderTime >= startOfMonth)
                    totalMonth += amt;

                // Parse tags/categories for the chart (e.g., "Groceries", "Food")
                // If details contain "Category: X", extract X. Or use item.title?
                // Let's rely on simple extraction from description if available, or just
                // "Uncategorized"
                String cat = "Misc";
                if (item.description.contains("Category:")) {
                    String[] parts = item.description.split("Category:");
                    if (parts.length > 1) {
                        cat = parts[1].trim().split("\n")[0];
                    }
                } else if (item.tags != null && !item.tags.isEmpty()) {
                    cat = item.tags.split(",")[0];
                }

                categoryTotals.put(cat, categoryTotals.getOrDefault(cat, 0.0) + amt);
            }
        }

        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        tvToday.setText(currencyFormat.format(totalToday));
        tvWeek.setText(currencyFormat.format(totalWeek));
        tvMonth.setText(currencyFormat.format(totalMonth));
        tvAllTime.setText(currencyFormat.format(totalAllTime));

        // Update Chart
        if (pieChart != null) {
            setupPieChart(pieChart, categoryTotals, totalAllTime);
        }
    }

    private void setupPieChart(com.github.mikephil.charting.charts.PieChart chart,
            java.util.Map<String, Double> dataMap, double total) {
        java.util.List<com.github.mikephil.charting.data.PieEntry> entries = new ArrayList<>();
        for (java.util.Map.Entry<String, Double> entry : dataMap.entrySet()) {
            entries.add(new com.github.mikephil.charting.data.PieEntry(entry.getValue().floatValue(), entry.getKey()));
        }

        com.github.mikephil.charting.data.PieDataSet dataSet = new com.github.mikephil.charting.data.PieDataSet(entries,
                "Expenses");

        // Colors
        java.util.List<Integer> colors = new ArrayList<>();
        int[] MATERIAL_COLORS = {
                android.graphics.Color.rgb(46, 204, 113), android.graphics.Color.rgb(52, 152, 219),
                android.graphics.Color.rgb(241, 196, 15), android.graphics.Color.rgb(231, 76, 60),
                android.graphics.Color.rgb(155, 89, 182), android.graphics.Color.rgb(52, 73, 94)
        };
        for (int c : MATERIAL_COLORS)
            colors.add(c);
        dataSet.setColors(colors);

        com.github.mikephil.charting.data.PieData data = new com.github.mikephil.charting.data.PieData(dataSet);
        data.setValueTextSize(12f);
        data.setValueTextColor(android.graphics.Color.WHITE);

        chart.setData(data);
        chart.setCenterText("Total\n₹" + (int) total);
        chart.setCenterTextSize(16f);
        chart.getDescription().setEnabled(false);
        chart.setHoleRadius(40f);
        chart.setTransparentCircleRadius(45f);
        chart.animateY(1000);
        chart.invalidate();
    }

    // --- Helper methods to get timestamps ---
    private long getStartOfDay() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long getStartOfWeek() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_WEEK, calendar.getFirstDayOfWeek());
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long getStartOfMonth() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    @Override
    public void onItemClick(ReminderItem item) {
        Intent intent = new Intent(getActivity(), DetailActivity.class);
        intent.putExtra(MainActivity.EXTRA_ID, item.id);
        startActivity(intent);
    }
}