package com.example.smartc;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
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
        binding = ActivityDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        reminderViewModel = new ViewModelProvider(this).get(ReminderViewModel.class);
        reminderCalendar = Calendar.getInstance();

        Intent intent = getIntent();
        if (intent.hasExtra(MainActivity.EXTRA_ID)) {
            // --- EDIT MODE ---
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
            // --- CREATE MODE ---
            isEditMode = false;
            setTitle("Create New Item");
            binding.buttonDelete.setVisibility(View.GONE);
        }

        binding.buttonSetReminder.setOnClickListener(v -> showDatePickerDialog());
        binding.buttonSave.setOnClickListener(v -> saveItem());
        binding.buttonDelete.setOnClickListener(v -> showDeleteConfirmationDialog());
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
            if (currentItem.isActive && currentItem.type.equals("REMINDER")) {
                ReminderManager.cancelReminder(this, currentItem);
            }
            reminderViewModel.delete(currentItem);
            Toast.makeText(this, "Item deleted", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void populateUI(ReminderItem item) {
        binding.editTextContent.setText(item.content);

        // Set the new TextView with the item's type
        binding.textViewItemType.setText("Type: " + item.type);

        if (item.type.equals("REMINDER")) {
            isReminderSet = true;
            reminderCalendar.setTimeInMillis(item.reminderTime);
            updateReminderDateTextView();
        }
    }

    private void saveItem() {
        String content = binding.editTextContent.getText().toString().trim();

        if (TextUtils.isEmpty(content)) {
            Toast.makeText(this, "Please enter some content", Toast.LENGTH_SHORT).show();
            return;
        }

        ReminderItem itemToSave = isEditMode ? currentItem : new ReminderItem();
        itemToSave.content = content;

        // --- Start of Corrected Logic ---

        // ALWAYS cancel the previous alarm when editing an active reminder.
        // This prevents the duplicate notification bug.
        if (isEditMode && currentItem.isActive && currentItem.type.equals("REMINDER")) {
            ReminderManager.cancelReminder(this, currentItem);
        }

        if (isReminderSet) {
            itemToSave.type = "REMINDER";
            itemToSave.reminderTime = reminderCalendar.getTimeInMillis();
            itemToSave.isActive = true;
            // Now, set the new (or rescheduled) alarm.
            ReminderManager.setReminder(this, itemToSave.reminderTime, "Reminder", itemToSave.content);
        } else {
            // If the date was removed or never set, it's a note.
            itemToSave.type = "NOTE";
            itemToSave.reminderTime = 0;
            itemToSave.isActive = false;
        }

        // --- End of Corrected Logic ---


        if(isEditMode) {
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