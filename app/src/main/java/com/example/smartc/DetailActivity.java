// This is the complete and final code for DetailActivity.java
// It correctly handles padding and the save/edit/delete logic.
package com.example.smartc;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import com.example.smartc.databinding.ActivityDetailBinding;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class DetailActivity extends AppCompatActivity {

    private ActivityDetailBinding binding;
    private Calendar reminderCalendar;
    private ReminderViewModel reminderViewModel;
    private boolean isReminderSet = false;
    private int currentItemId = -1;
    private ReminderItem currentItem;
    private boolean isEditMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        reminderViewModel = new ViewModelProvider(this).get(ReminderViewModel.class);
        reminderCalendar = Calendar.getInstance();

        Intent intent = getIntent();
        if (intent.hasExtra(MainActivity.EXTRA_ID)) {
            isEditMode = true;
            currentItemId = intent.getIntExtra(MainActivity.EXTRA_ID, -1);
            setTitle("Edit Item");
            binding.buttonSave.setText("Save Changes");
            binding.buttonDelete.setVisibility(View.VISIBLE);

            reminderViewModel.getById(currentItemId).observe(this, item -> {
                if (item != null) {
                    currentItem = item;
                    populateUI(item);
                }
            });
        } else {
            isEditMode = false;
            setTitle("Create New Item");
            binding.buttonDelete.setVisibility(View.GONE);
        }

        binding.buttonAddReminder.setOnClickListener(v -> showDatePickerDialog());
        binding.buttonDelete.setOnClickListener(v -> showDeleteConfirmationDialog());
        binding.buttonSave.setOnClickListener(v -> saveItem());
    }

    private void showDeleteConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Item")
                .setMessage("Are you sure you want to delete this item?")
                .setPositiveButton("Delete", (dialog, which) -> deleteItem())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteItem() {
        if (currentItem != null) {
            if (currentItem.isActive && "REMINDER".equals(currentItem.type)) {
                ReminderManager.cancelReminder(this, currentItem);
            }
            reminderViewModel.delete(currentItem);
            Toast.makeText(this, "Item deleted", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void populateUI(ReminderItem item) {
        binding.editTextTitle.setText(item.title);
        binding.editTextDescription.setText(item.description);
        if ("REMINDER".equals(item.type) || item.reminderTime > 0) {
            isReminderSet = true;
            reminderCalendar.setTimeInMillis(item.reminderTime);
            updateReminderDateTextView();
        }
    }

    private void saveItem() {
        String title = binding.editTextTitle.getText().toString().trim();
        String description = binding.editTextDescription.getText().toString().trim();

        if (TextUtils.isEmpty(title)) {
            Toast.makeText(this, "Please enter a title", Toast.LENGTH_SHORT).show();
            return;
        }

        final ReminderItem itemToSave;
        if (isEditMode) itemToSave = currentItem;
        else itemToSave = new ReminderItem();

        itemToSave.title = title;
        itemToSave.description = description;

        if (isEditMode && itemToSave.isActive) {
            ReminderManager.cancelReminder(this, itemToSave);
        }

        if (isReminderSet) {
            itemToSave.type = "REMINDER"; // We can enhance this later
            itemToSave.reminderTime = reminderCalendar.getTimeInMillis();
            itemToSave.isActive = true;
            ReminderManager.setReminder(this, itemToSave.reminderTime, itemToSave.title, itemToSave.description);
        } else {
            itemToSave.type = "NOTE"; // We can enhance this later
            itemToSave.reminderTime = 0;
            itemToSave.isActive = false;
        }

        if (isEditMode) {
            reminderViewModel.update(itemToSave);
            Toast.makeText(this, "Item updated!", Toast.LENGTH_SHORT).show();
        } else {
            reminderViewModel.insert(itemToSave);
            Toast.makeText(this, "Item saved!", Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    private void showDatePickerDialog() {
        int year = reminderCalendar.get(Calendar.YEAR);
        int month = reminderCalendar.get(Calendar.MONTH);
        int day = reminderCalendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, selectedYear, selectedMonth, selectedDayOfMonth) -> {
                    reminderCalendar.set(Calendar.YEAR, selectedYear);
                    reminderCalendar.set(Calendar.MONTH, selectedMonth);
                    reminderCalendar.set(Calendar.DAY_OF_MONTH, selectedDayOfMonth);
                    showTimePickerDialog();
                }, year, month, day);
        datePickerDialog.show();
    }

    private void showTimePickerDialog() {
        int hour = reminderCalendar.get(Calendar.HOUR_OF_DAY);
        int minute = reminderCalendar.get(Calendar.MINUTE);

        TimePickerDialog timePickerDialog = new TimePickerDialog(this,
                (view, selectedHourOfDay, selectedMinute) -> {
                    reminderCalendar.set(Calendar.HOUR_OF_DAY, selectedHourOfDay);
                    reminderCalendar.set(Calendar.MINUTE, selectedMinute);
                    isReminderSet = true;
                    updateReminderDateTextView();
                }, hour, minute, false);
        timePickerDialog.show();
    }

    private void updateReminderDateTextView() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
        String formattedDate = sdf.format(reminderCalendar.getTime());
        binding.textViewSelectedDate.setText("Reminder set for: " + formattedDate);
        binding.textViewSelectedDate.setVisibility(View.VISIBLE);
    }
}