package com.example.smartc;

import org.json.JSONArray; // ✅ The missing import
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
import com.google.ai.client.generativeai.type.GenerationConfig;
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

    private static final String TAG = "AI_DEBUG";

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
        if (System.currentTimeMillis() - lastApiCallTime < 10000) {
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

        String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        String textPromptTemplate = "Your SOLE TASK is to analyze the content and respond ONLY with a valid JSON array that conforms to the following schema. The current date is %s. " +
                "Do not include any extra text, explanations, or markdown. Your response must be a raw JSON array starting with [ and ending with ].\n\n" +
                "**JSON Schema:**\n" +
                "[\n" +
                "  {\n" +
                "    \"category\": \"(String) One of: BILL, RECEIPT, TICKET, TASK, NOTE\",\n" +
                "    \"title\": \"(String) A short summary of the item.\",\n" +
                "    \"description\": \"(String) A detailed summary. If the category is 'NOTE', paraphrase the original content.\",\n" +
                "    \"tags\": \"(String Array) 1-3 relevant, lowercase tags.\",\n" +
                "    \"amount\": \"(String) The monetary value for a BILL or RECEIPT. For all others, use 'N/A'.\",\n" +
                "    \"reminder\": {\n" +
                "      \"is_reminder\": \"(Boolean) true for BILL, TICKET, TASK. false for others.\",\n" +
                "      \"date\": \"(String) Date in YYYY-MM-DD format. For a RECEIPT with no date, use the current date (%s). For others with no date, use 'N/A'.\",\n" +
                "      \"time\": \"(String) Time in HH:mm format. Default to '09:00' if a date exists but no time is found. Use 'N/A' if no date.\"\n" +
                "    }\n" +
                "  }\n" +
                "]";

        String finalPrompt = String.format(textPromptTemplate, currentDate, currentDate);

        Content content = new Content.Builder().addText(finalPrompt + "\n\nHere is the text to analyze:\n" + combinedText).build();

        // START: New Logging
        String exampleJson = "[{\"category\":\"...\",\"title\":\"...\",\"description\":\"...\",\"tags\":[\"...\"],\"amount\":\"...\",\"reminder\":{...}}]";
        Log.d("AI_DEBUG", "EXPECTED JSON FORMAT: " + exampleJson);
        Log.d("AI_DEBUG", "Preparing to call Gemini API...");
        // END: New Logging

        ListenableFuture<GenerateContentResponse> future = generativeModel.generateContent(content);

        Futures.addCallback(future, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                lastApiCallTime = System.currentTimeMillis();
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    // Log the actual response received
                    Log.d("AI_DEBUG", "RECEIVED RAW RESPONSE: " + result.getText());
                    processAndSaveAiResponse(result.getText());
                });
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e("AI_FAILURE", "Full API Error: ", t);
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(DetailActivity.this, "AI analysis failed: " + t.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }, backgroundExecutor);
    }
    // Use this method in BOTH ShareActivity.java and DetailActivity.java
    private void processAndSaveAiResponse(String responseText) {
        Log.d("AI_DEBUG", "RECEIVED RAW RESPONSE: " + responseText);
        if (responseText == null) {
            showError("AI returned an empty response.");
            return;
        }

        // START: JSON Cleaning Logic
        int startIndex = responseText.indexOf("[");
        int endIndex = responseText.lastIndexOf("]");

        if (startIndex == -1 || endIndex == -1 || endIndex < startIndex) {
            showError("Could not find valid JSON in the AI response.");
            return;
        }

        String jsonString = responseText.substring(startIndex, endIndex + 1);
        Log.d("AI_DEBUG", "EXTRACTED JSON: " + jsonString);
        // END: JSON Cleaning Logic

        try {
            JSONArray jsonArray = new JSONArray(jsonString);
            if (jsonArray.length() == 0) {
                Toast.makeText(this, "AI could not find any items to save.", Toast.LENGTH_SHORT).show();
                return;
            }

            // In DetailActivity, "Smart Analyze" only processes the FIRST item found.
            JSONObject json = jsonArray.getJSONObject(0);

            ReminderItem itemToSave = isEditMode ? currentItem : new ReminderItem();
            itemToSave.category = json.getString("category");
            itemToSave.title = json.getString("title");
            itemToSave.description = json.getString("description");
            itemToSave.amount = parseAmount(json.getString("amount"));
            itemToSave.tags = json.getJSONArray("tags").toString();

            if (isEditMode && itemToSave.isActive) {
                ReminderManager.cancelReminder(this, itemToSave);
            }

            JSONObject reminderObject = json.getJSONObject("reminder");
            boolean isReminder = reminderObject.getBoolean("is_reminder");
            String dateStr = reminderObject.getString("date");

            if (isReminder && !"N/A".equals(dateStr)) {
                String timeStr = reminderObject.getString("time");
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
                Date date = sdf.parse(dateStr + " " + timeStr);
                itemToSave.reminderTime = date.getTime();
                itemToSave.isActive = true;
                ReminderManager.setReminder(this, itemToSave.reminderTime, itemToSave.title, itemToSave.description);
            } else if ("RECEIPT".equals(itemToSave.category)) {
                if (!"N/A".equals(dateStr)) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                    Date date = sdf.parse(dateStr);
                    itemToSave.reminderTime = date.getTime();
                } else {
                    itemToSave.reminderTime = System.currentTimeMillis();
                }
                itemToSave.isActive = false;
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
            showError("Could not process AI response.");
        }
    }

    private void markAsExpense() {
        final ReminderItem itemToMark = isEditMode ? currentItem : new ReminderItem();
        if (!isEditMode) {
            itemToMark.title = binding.editTextTitle.getText().toString().trim();
            itemToMark.description = binding.editTextDescription.getText().toString().trim();
        }

        if (TextUtils.isEmpty(itemToMark.title)) {
            Toast.makeText(this, "Please enter a title first.", Toast.LENGTH_SHORT).show();
            return;
        }

        itemToMark.category = "RECEIPT";
        itemToMark.reminderTime = System.currentTimeMillis(); // Set transaction date to NOW
        itemToMark.isActive = false;
        String combinedText = itemToMark.title + " " + itemToMark.description;
        itemToMark.amount = parseAmount(combinedText);

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

        final ReminderItem itemToSave = isEditMode ? currentItem : (currentItem != null ? currentItem : new ReminderItem());
        if (!isEditMode && !"RECEIPT".equals(itemToSave.category)) {
            itemToSave.category = "NOTE";
        }

        itemToSave.title = title;
        itemToSave.description = description;

        if (isEditMode && itemToSave.isActive) {
            ReminderManager.cancelReminder(this, itemToSave);
        }

        if (isReminderSet) {
            if (!"BILL".equals(itemToSave.category) && !"TICKET".equals(itemToSave.category) && !"RECEIPT".equals(itemToSave.category)) {
                itemToSave.category = "TASK";
            }
            itemToSave.reminderTime = reminderCalendar.getTimeInMillis();
            itemToSave.isActive = true;
            ReminderManager.setReminder(this, itemToSave.reminderTime, itemToSave.title, itemToSave.description);
        } else {
            itemToSave.isActive = false;
            if ("RECEIPT".equals(itemToSave.category)) {
                if (itemToSave.reminderTime == 0) {
                    itemToSave.reminderTime = System.currentTimeMillis();
                }
            } else {
                itemToSave.category = "NOTE";
                itemToSave.reminderTime = 0;
            }
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
    private void removeReminder() {
        isReminderSet = false;
        binding.reminderDetailsLayout.setVisibility(View.GONE);
        binding.buttonAddReminder.setText("Add Reminder");
        binding.chipRemoveReminder.setVisibility(View.GONE);
        Toast.makeText(this, "Reminder removed. Click 'Save Changes' to confirm.", Toast.LENGTH_SHORT).show();
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

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        // Handler to close the activity after a delay if it's an unrecoverable error
        if (!isEditMode) { // Only finish if it's a new item creation that failed
            new android.os.Handler(getMainLooper()).postDelayed(this::finish, 3000);
        }
    }
    private double parseAmount(String amountStr) {
        if (amountStr == null || amountStr.equalsIgnoreCase("N/A")) {
            return 0.0;
        }
        try {
            // This removes currency symbols, commas, and letters, then converts to a number
            String cleanStr = amountStr.toLowerCase()
                    .replace("rs", "")
                    .replace("inr", "")
                    .replace("₹", "")
                    .replaceAll(",", "")
                    .trim();
            if (cleanStr.isEmpty()) return 0.0;
            return Double.parseDouble(cleanStr);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}