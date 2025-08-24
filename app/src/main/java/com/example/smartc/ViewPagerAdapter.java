package com.example.smartc;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import java.util.ArrayList;
import java.util.Arrays;

public class ViewPagerAdapter extends FragmentStateAdapter {

    public ViewPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                // Reminders Tab: Shows BILL, TICKET, and TASK categories
                return ItemListFragment.newInstance(new ArrayList<>(Arrays.asList("ALL_REMINDERS")));
            case 1:
                // Notes Tab: Shows NOTE category
                return ItemListFragment.newInstance(new ArrayList<>(Arrays.asList("NOTE")));
            case 2:
                // Expenses Tab: Shows RECEIPT category
                return ItemListFragment.newInstance(new ArrayList<>(Arrays.asList("RECEIPT")));
            default:
                return ItemListFragment.newInstance(new ArrayList<>());
        }
    }

    @Override
    public int getItemCount() {
        return 3; // We have 3 tabs
    }
}