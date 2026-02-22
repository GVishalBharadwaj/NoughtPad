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
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
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

    // Period filter state
    private static final int PERIOD_TODAY = 0;
    private static final int PERIOD_WEEK = 1;
    private static final int PERIOD_MONTH = 2;
    private static final int PERIOD_ALL = 3;
    private int currentPeriod = PERIOD_MONTH; // Default to month

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

        boolean isExpenseTab = categoriesToShow != null && categoriesToShow.contains("RECEIPT");

        // Setup period filter chips for expense tab
        if (isExpenseTab) {
            setupPeriodFilterChips(view);
        }

        // This single observer is our "source of truth" for all UI updates
        reminderViewModel.getAllItems().observe(getViewLifecycleOwner(), allItems -> {
            if (allItems == null)
                return;

            // --- 1. Filter the list for the RecyclerView ---
            List<ReminderItem> filteredList = new ArrayList<>();
            if (categoriesToShow.contains("ALL_REMINDERS")) {
                for (ReminderItem item : allItems) {
                    if ("TICKET".equals(item.category)
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

            // --- 2. If this is the Expenses tab, apply period filter & calculations ---
            if (isExpenseTab) {
                long periodStart = getPeriodStartTimestamp(currentPeriod);
                List<ReminderItem> periodFiltered = new ArrayList<>();
                for (ReminderItem item : filteredList) {
                    if (currentPeriod == PERIOD_ALL || item.reminderTime >= periodStart) {
                        periodFiltered.add(item);
                    }
                }
                adapter.submitList(periodFiltered);
                calculateAndDisplayExpenses(view, periodFiltered);
            } else {
                adapter.submitList(filteredList);
            }
        });
    }

    private void setupPeriodFilterChips(View view) {
        ChipGroup chipGroup = view.findViewById(R.id.chip_group_period);
        Chip chipMonth = view.findViewById(R.id.chip_month);
        if (chipGroup == null || chipMonth == null)
            return;

        // Default to Month
        chipMonth.setChecked(true);

        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty())
                return;
            int checkedId = checkedIds.get(0);
            if (checkedId == R.id.chip_today) {
                currentPeriod = PERIOD_TODAY;
            } else if (checkedId == R.id.chip_week) {
                currentPeriod = PERIOD_WEEK;
            } else if (checkedId == R.id.chip_month) {
                currentPeriod = PERIOD_MONTH;
            } else if (checkedId == R.id.chip_all) {
                currentPeriod = PERIOD_ALL;
            }
            // Trigger re-observation by getting current data
            List<ReminderItem> current = reminderViewModel.getAllItems().getValue();
            if (current != null) {
                refreshExpenseData(view, current);
            }
        });
    }

    private void refreshExpenseData(View view, List<ReminderItem> allItems) {
        List<ReminderItem> filteredList = new ArrayList<>();
        for (ReminderItem item : allItems) {
            if ("RECEIPT".equals(item.category)) {
                filteredList.add(item);
            }
        }

        long periodStart = getPeriodStartTimestamp(currentPeriod);
        List<ReminderItem> periodFiltered = new ArrayList<>();
        for (ReminderItem item : filteredList) {
            if (currentPeriod == PERIOD_ALL || item.reminderTime >= periodStart) {
                periodFiltered.add(item);
            }
        }
        adapter.submitList(periodFiltered);
        calculateAndDisplayExpenses(view, periodFiltered);
    }

    private long getPeriodStartTimestamp(int period) {
        switch (period) {
            case PERIOD_TODAY:
                return getStartOfDay();
            case PERIOD_WEEK:
                return getStartOfWeek();
            case PERIOD_MONTH:
                return getStartOfMonth();
            default:
                return 0;
        }
    }

    private void calculateAndDisplayExpenses(View view, List<ReminderItem> items) {
        final TextView tvBalance = view.findViewById(R.id.text_total_balance);
        final TextView tvIncome = view.findViewById(R.id.text_total_income);
        final TextView tvExpense = view.findViewById(R.id.text_total_expense);
        final com.github.mikephil.charting.charts.PieChart pieChart = view.findViewById(R.id.pieChart);

        double totalIncome = 0;
        double totalExpense = 0;

        // Aggregation for Chart
        java.util.Map<String, Double> categoryTotals = new java.util.LinkedHashMap<>();

        for (ReminderItem item : items) {
            boolean isIncome = "income".equalsIgnoreCase(item.type);
            double amt = item.amount;

            if (isIncome) {
                totalIncome += amt;
            } else {
                totalExpense += amt;

                // Category from tags (primary) or fallback
                String cat = "General";
                if (item.tags != null && !item.tags.isEmpty()) {
                    cat = item.tags.split(",")[0];
                }
                categoryTotals.put(cat, categoryTotals.getOrDefault(cat, 0.0) + amt);
            }
        }

        double balance = totalIncome - totalExpense;

        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        if (tvBalance != null)
            tvBalance.setText(currencyFormat.format(balance));
        if (tvIncome != null)
            tvIncome.setText(currencyFormat.format(totalIncome));
        if (tvExpense != null)
            tvExpense.setText(currencyFormat.format(totalExpense));

        // Update Chart
        if (pieChart != null) {
            setupPieChart(pieChart, categoryTotals, totalExpense);
        }
    }

    private void setupPieChart(com.github.mikephil.charting.charts.PieChart chart,
            java.util.Map<String, Double> dataMap, double total) {
        java.util.List<com.github.mikephil.charting.data.PieEntry> entries = new ArrayList<>();
        for (java.util.Map.Entry<String, Double> entry : dataMap.entrySet()) {
            entries.add(new com.github.mikephil.charting.data.PieEntry(entry.getValue().floatValue(), entry.getKey()));
        }

        if (entries.isEmpty()) {
            chart.clear();
            chart.setCenterText("No Data");
            chart.setCenterTextColor(android.graphics.Color.parseColor("#9E9E9E"));
            chart.setCenterTextSize(14f);
            chart.invalidate();
            return;
        }

        com.github.mikephil.charting.data.PieDataSet dataSet = new com.github.mikephil.charting.data.PieDataSet(entries,
                "");

        // Premium Slate & Teal Spectrum
        java.util.List<Integer> colors = new ArrayList<>();
        int[] CHART_COLORS = {
                android.graphics.Color.parseColor("#1DE9B6"), // Neon Teal
                android.graphics.Color.parseColor("#38BDF8"), // Sky Blue
                android.graphics.Color.parseColor("#4ADE80"), // Bright Green
                android.graphics.Color.parseColor("#818CF8"), // Indigo
                android.graphics.Color.parseColor("#F472B6"), // Pink (Accents only)
                android.graphics.Color.parseColor("#FBBF24"), // Amber
                android.graphics.Color.parseColor("#64748B"), // Slate
        };
        for (int c : CHART_COLORS)
            colors.add(c);
        dataSet.setColors(colors);
        dataSet.setSliceSpace(2f);
        dataSet.setValueLinePart1OffsetPercentage(80f);

        com.github.mikephil.charting.data.PieData data = new com.github.mikephil.charting.data.PieData(dataSet);
        data.setValueTextSize(11f);
        data.setValueTextColor(android.graphics.Color.WHITE);

        chart.setData(data);
        chart.setCenterText("₹" + String.format(Locale.getDefault(), "%,.0f", total));
        chart.setCenterTextSize(18f);
        chart.setCenterTextColor(android.graphics.Color.WHITE);
        chart.getDescription().setEnabled(false);
        chart.setHoleRadius(50f);
        chart.setTransparentCircleRadius(55f);
        chart.setHoleColor(android.graphics.Color.parseColor("#0F0F14"));
        chart.setDrawEntryLabels(false);

        // Legend
        com.github.mikephil.charting.components.Legend l = chart.getLegend();
        l.setVerticalAlignment(com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.BOTTOM);
        l.setHorizontalAlignment(com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.CENTER);
        l.setOrientation(com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL);
        l.setDrawInside(false);
        l.setWordWrapEnabled(true);
        l.setTextColor(android.graphics.Color.parseColor("#E4E1E6"));
        l.setXEntrySpace(12f);
        l.setYEntrySpace(4f);

        chart.animateY(800);
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