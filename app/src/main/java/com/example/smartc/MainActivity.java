package com.example.smartc;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.smartc.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private ReminderViewModel reminderViewModel;
    private ReminderAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Set up the RecyclerView
        binding.recyclerview.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ReminderAdapter();
        binding.recyclerview.setAdapter(adapter);

        // Get the ViewModel
        reminderViewModel = new ViewModelProvider(this).get(ReminderViewModel.class);

        // ✅ --- CORRECTED AND SIMPLIFIED OBSERVER ---
        // Observe the LiveData from the ViewModel
        reminderViewModel.getAllItems().observe(this, items -> {
            // This is the only line we need. ListAdapter will handle the rest.
            adapter.submitList(items);

            // Show a message if the list from the database is truly empty
            if (items == null || items.isEmpty()) {
                binding.textviewEmpty.setVisibility(View.VISIBLE);
                binding.recyclerview.setVisibility(View.GONE);
            } else {
                binding.textviewEmpty.setVisibility(View.GONE);
                binding.recyclerview.setVisibility(View.VISIBLE);
            }
        });

        // Set up the listener for the delete button (this part is unchanged)
        adapter.setOnItemDeleteListener(item -> {
            ReminderManager.cancelReminder(this, item);
            reminderViewModel.delete(item);
        });
    }
}