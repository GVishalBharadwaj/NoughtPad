package com.example.smartc;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import com.example.smartc.databinding.ActivityDetailBinding;
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class DetailActivity extends AppCompatActivity {

    private ActivityDetailBinding binding;
    private Calendar reminderCalendar;
    private ReminderViewModel reminderViewModel;
    private boolean isReminderSet = false;
    private int currentItemId = -1;
    private ReminderItem currentItem;
    private boolean isEditMode = false;
    private GenerativeModelFutures generativeModel;
    private final Executor backgroundExecutor = Executors.newSingleThreadExecutor();
    private long lastApiCallTime = 0;


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

        GenerativeModel gm = new GenerativeModel("gemini-2.0-flash-lite", BuildConfig.GEMINI_API_KEY);
        generativeModel = GenerativeModelFutures.from(gm);

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
                    this.currentItem = item;
                    populateUI(item);
                }
            });
        } else {
            // --- CREATE MODE ---
            isEditMode = false;
            setTitle("Create New Item");
            binding.buttonDelete.setVisibility(View.GONE);
            binding.chipMarkExpense.setVisibility(View.VISIBLE); // Show "Mark as Expense" in create mode
            binding.chipRemoveReminder.setVisibility(View.GONE);
        }

        binding.buttonAddReminder.setOnClickListener(v -> showDatePickerDialog());
        binding.buttonDelete.setOnClickListener(v -> showDeleteConfirmationDialog());
        binding.buttonSave.setOnClickListener(v -> saveItemManually());
        binding.buttonSmartAnalyze.setOnClickListener(v -> analyzeTextWithAi());
        binding.chipRemoveReminder.setOnClickListener(v -> removeReminder());
        binding.chipMarkExpense.setOnClickListener(v -> markAsExpense());
    }

    private void populateUI(ReminderItem item) {
        binding.editTextTitle.setText(item.title);
        binding.editTextDescription.setText(item.description);

        if ("RECEIPT".equals(item.category)) {
            binding.chipMarkExpense.setVisibility(View.GONE);
        } else {
            binding.chipMarkExpense.setVisibility(View.VISIBLE);
        }

        if (item.reminderTime > 0 && item.isActive) {
            isReminderSet = true;
            reminderCalendar.setTimeInMillis(item.reminderTime);
            updateReminderDateTextView();
            binding.chipRemoveReminder.setVisibility(View.VISIBLE);
        } else {
            isReminderSet = false;
            binding.chipRemoveReminder.setVisibility(View.GONE);
        }
    }

    private void analyzeTextWithAi() {
        if (System.currentTimeMillis() - lastApiCallTime < 10000) { // 10000 milliseconds = 10 seconds
            Toast.makeText(this, "Please wait a moment before analyzing again.", Toast.LENGTH_SHORT).show();
            return;
        }
        String title = binding.editTextTitle.getText().toString().trim();
        String description = binding.editTextDescription.getText().toString().trim();
        String combinedText = title + "\n" + description;

        if (TextUtils.isEmpty(combinedText)) {
            Toast.makeText(this, "Please enter some text to analyze.", Toast.LENGTH_SHORT).show();
            return;
        }

        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Analyzing...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        String currentDate = new SimpleDateFormat("MMMM dd, yyyy", Locale.US).format(new Date());
        String textPromptTemplate = "Your SOLE TASK is to analyze the content (image or text) and respond with a single, valid JSON object and NOTHING ELSE. Your entire response must be ONLY the JSON object. Do not include any explanatory text, greetings, or markdown formatting like ```json. The current date is %s. " +
                "First, determine the primary category from this list: [\"BILL\", \"RECEIPT\", \"TICKET\", \"TASK\", \"NOTE\"]. " +
                "A \"BILL\" is a request for future payment. A \"RECEIPT\" is proof of a past payment. A \"TICKET\" is for an event. A \"TASK\" is a direct command. A \"NOTE\" is everything else. " +
                "Second, create a JSON object with these keys: \"category\", \"title\", \"description\", \"tags\", \"amount\", and a nested \"reminder\" object. " +
                "\"description\" is the most important field; if the category is 'NOTE', paraphrase the original text to improve clarity and style. For all other categories, provide a detailed summary of all information. " +
                "\"reminder\" is an object containing \"is_reminder\" (boolean), \"date\" (YYYY-MM-DD or \"N/A\"), and \"time\" (HH:mm, default to \"09:00\" if not found). " +
                "Example for a task: {\"category\":\"TASK\",\"title\":\"Wish Vishal Happy Birthday\",\"description\":\"remind me to wish vishal happy birthday on august 28th\",\"tags\":[\"birthday\",\"personal\"],\"amount\":\"N/A\",\"reminder\":{\"is_reminder\":true,\"date\":\"2025-08-28\",\"time\":\"09:00\"}}";
        String finalPrompt = String.format(textPromptTemplate, currentDate);

        Content content = new Content.Builder().addText(finalPrompt + "\n\nHere is the text to analyze:\n" + combinedText).build();
        ListenableFuture<GenerateContentResponse> future = generativeModel.generateContent(content);

        Futures.addCallback(future, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    processAndSaveAiResponse(result.getText());
                });
            }

            @Override
            public void onFailure(Throwable t) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(DetailActivity.this, "AI analysis failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }, backgroundExecutor);
    }

    private void processAndSaveAiResponse(String responseText) {
        if (responseText == null || !responseText.contains("{") || !responseText.contains("}")) {
            Toast.makeText(this, "AI response was not in the expected format.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String jsonString = responseText.substring(responseText.indexOf("{"), responseText.lastIndexOf("}") + 1);
            JSONObject json = new JSONObject(jsonString);

            ReminderItem itemToSave = isEditMode ? currentItem : new ReminderItem();
            itemToSave.category = json.getString("category");
            itemToSave.title = json.getString("title");
            itemToSave.description = json.getString("description");
            itemToSave.amount = json.getString("amount");
            itemToSave.tags = json.getJSONArray("tags").toString();

            if (isEditMode && itemToSave.isActive) {
                ReminderManager.cancelReminder(this, itemToSave);
            }

            JSONObject reminderObject = json.getJSONObject("reminder");
            if (reminderObject.getBoolean("is_reminder")) {
                String dateStr = reminderObject.getString("date");
                String timeStr = reminderObject.getString("time");
                if (!"N/A".equals(dateStr)) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
                    Date date = sdf.parse(dateStr + " " + timeStr);
                    itemToSave.reminderTime = date.getTime();
                    itemToSave.isActive = true;
                    ReminderManager.setReminder(this, itemToSave.reminderTime, itemToSave.title, itemToSave.description);
                } else {
                    itemToSave.reminderTime = 0;
                    itemToSave.isActive = false;
                }
            } else {
                itemToSave.reminderTime = 0;
                itemToSave.isActive = false;
            }

            if (isEditMode) {
                reminderViewModel.update(itemToSave);
                Toast.makeText(this, itemToSave.category + " updated via AI!", Toast.LENGTH_SHORT).show();
            } else {
                reminderViewModel.insert(itemToSave);
                Toast.makeText(this, itemToSave.category + " saved via AI!", Toast.LENGTH_SHORT).show();
            }
            finish();

        } catch (JSONException | ParseException e) {
            Log.e("AI_SAVE_ERROR", "Error processing or saving AI response", e);
            Toast.makeText(this, "Could not process AI response.", Toast.LENGTH_SHORT).show();
        }
    }

    private void removeReminder() {
        isReminderSet = false;
        binding.reminderDetailsLayout.setVisibility(View.GONE);
        binding.buttonAddReminder.setText("Add Reminder");
        binding.chipRemoveReminder.setVisibility(View.GONE);
        Toast.makeText(this, "Reminder removed. Click 'Save Changes' to confirm.", Toast.LENGTH_SHORT).show();
    }

    private void markAsExpense() {
        final ReminderItem itemToMark = isEditMode ? currentItem : new ReminderItem();

        // Populate with current text if it's a new item
        if (!isEditMode) {
            itemToMark.title = binding.editTextTitle.getText().toString().trim();
            itemToMark.description = binding.editTextDescription.getText().toString().trim();
        }

        itemToMark.category = "RECEIPT";

        if (isEditMode) {
            reminderViewModel.update(itemToMark);
            Toast.makeText(this, "Item moved to Expenses", Toast.LENGTH_SHORT).show();
        } else {
            reminderViewModel.insert(itemToMark);
            Toast.makeText(this, "Expense saved!", Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    private void saveItemManually() {
        String title = binding.editTextTitle.getText().toString().trim();
        String description = binding.editTextDescription.getText().toString().trim();

        if (TextUtils.isEmpty(title)) {
            Toast.makeText(this, "Please enter a title", Toast.LENGTH_SHORT).show();
            return;
        }

        final ReminderItem itemToSave = isEditMode ? currentItem : new ReminderItem();
        if (!isEditMode) {
            // If user marked it as expense before saving, respect that
            if (!"RECEIPT".equals(itemToSave.category)) {
                itemToSave.category = "NOTE";
            }
        }

        itemToSave.title = title;
        itemToSave.description = description;

        if (isEditMode && itemToSave.isActive) {
            ReminderManager.cancelReminder(this, itemToSave);
        }

        if (isReminderSet) {
            if (!"BILL".equals(itemToSave.category) && !"TICKET".equals(itemToSave.category)) {
                itemToSave.category = "TASK";
            }
            itemToSave.reminderTime = reminderCalendar.getTimeInMillis();
            itemToSave.isActive = true;
            ReminderManager.setReminder(this, itemToSave.reminderTime, itemToSave.title, itemToSave.description);
        } else {
            if (!"RECEIPT".equals(itemToSave.category)) {
                itemToSave.category = "NOTE";
            }
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
            if (currentItem.isActive) {
                ReminderManager.cancelReminder(this, currentItem);
            }
            reminderViewModel.delete(currentItem);
            Toast.makeText(this, "Item deleted", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void showDatePickerDialog() {
        new DatePickerDialog(this, (view, year, month, day) -> {
            reminderCalendar.set(Calendar.YEAR, year);
            reminderCalendar.set(Calendar.MONTH, month);
            reminderCalendar.set(Calendar.DAY_OF_MONTH, day);
            showTimePickerDialog();
        }, reminderCalendar.get(Calendar.YEAR), reminderCalendar.get(Calendar.MONTH), reminderCalendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTimePickerDialog() {
        new TimePickerDialog(this, (view, hour, minute) -> {
            reminderCalendar.set(Calendar.HOUR_OF_DAY, hour);
            reminderCalendar.set(Calendar.MINUTE, minute);
            isReminderSet = true;
            updateReminderDateTextView();
        }, reminderCalendar.get(Calendar.HOUR_OF_DAY), reminderCalendar.get(Calendar.MINUTE), false).show();
    }

    private void updateReminderDateTextView() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
        String formattedDate = sdf.format(reminderCalendar.getTime());
        binding.reminderDetailsLayout.setVisibility(View.VISIBLE);
        binding.textViewSelectedDate.setText(formattedDate);
        binding.buttonAddReminder.setText("Edit Reminder");
        binding.chipRemoveReminder.setVisibility(View.VISIBLE);
    }
}