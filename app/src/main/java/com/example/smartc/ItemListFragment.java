package com.example.smartc;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

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
        return inflater.inflate(R.layout.fragment_item_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView recyclerView = view.findViewById(R.id.recycler_view_fragment);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ReminderAdapter();
        adapter.setOnItemClickListener(this);
        recyclerView.setAdapter(adapter);

        // This is the CRITICAL line. It gets the ViewModel from the PARENT ACTIVITY (MainActivity).
        reminderViewModel = new ViewModelProvider(requireActivity()).get(ReminderViewModel.class);

        reminderViewModel.getAllItems().observe(getViewLifecycleOwner(), allItems -> {
            List<ReminderItem> filteredList = new ArrayList<>();
            if (allItems == null) return;

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
        });
    }

    @Override
    public void onItemClick(ReminderItem item) {
        Intent intent = new Intent(getActivity(), DetailActivity.class);
        intent.putExtra(MainActivity.EXTRA_ID, item.id);
        startActivity(intent);
    }
}