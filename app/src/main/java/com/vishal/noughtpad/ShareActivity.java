package com.vishal.noughtpad;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import org.json.JSONArray; // ✅ The missing import
import java.util.ArrayList;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import java.util.List;
import com.vishal.noughtpad.databinding.ActivityShareBinding;
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

// This is the import for BuildConfig
import com.vishal.noughtpad.BuildConfig;

public class ShareActivity extends AppCompatActivity {

    private ActivityShareBinding binding;
    private GenerativeModelFutures generativeModel;
    private ReminderViewModel reminderViewModel;
    private final Executor backgroundExecutor = Executors.newSingleThreadExecutor();
    private EmbeddingHelper embeddingHelper; // Add this

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityShareBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Using the secure BuildConfig method is the correct practice
        GenerativeModel gm = new GenerativeModel(
                "gemini-2.5-flash-lite", // Correct, valid model name
                BuildConfig.GEMINI_API_KEY
        );
        generativeModel = GenerativeModelFutures.from(gm);
        embeddingHelper = new EmbeddingHelper(); // Initialize the helper

        reminderViewModel = new ViewModelProvider(this).get(ReminderViewModel.class);
        handleIntent(getIntent());
    }

    private void handleIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) {
            finish();
            return;
        }

        // 1. Read the setting value from SharedPreferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean useCustomTitle = prefs.getBoolean("custom_title_toggle", false);

        // 2. (FOR DEBUGGING) Print the value to the Logcat
        Log.d("SETTINGS_CHECK", "Value of 'custom_title_toggle' is: " + useCustomTitle);

        // 3. Decide which action to take based on the setting
        if (useCustomTitle) {
            // If setting is ON, show the dialog to get a title first
            showCustomTitleDialog(intent);
        } else {
            // If setting is OFF, proceed directly to analysis
            String type = intent.getType();
            if (type != null) {
                if (type.startsWith("image/")) {
                    Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                    if (imageUri != null) {
                        analyzeImage(imageUri, null);
                    } else {
                        finish();
                    }
                } else if ("text/plain".equals(type)) {
                    String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                    if (sharedText != null) {
                        analyzeText(sharedText, null);
                    } else {
                        finish();
                    }
                }
            } else {
                finish();
            }
        }
    }

    private void showCustomTitleDialog(Intent intent) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Set a Custom Title (Optional)");

        // Inflate (create) the custom layout view
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_custom_title, null);

        // Get the EditText from inside our custom layout
        final EditText input = dialogView.findViewById(R.id.edit_text_custom_title);

        // Set the custom layout as the content of the dialog
        builder.setView(dialogView);

        // This is the "Continue" button logic
        builder.setPositiveButton("Continue", (dialog, which) -> {
            String customTitle = input.getText().toString().trim();
            String type = intent.getType();
            if (type != null) {
                if (type.startsWith("image/")) {
                    Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                    if (imageUri != null) analyzeImage(imageUri, customTitle);
                } else if ("text/plain".equals(type)) {
                    String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                    if (sharedText != null) analyzeText(sharedText, customTitle);
                }
            }
        });

        // This is the "Skip" button logic
        builder.setNegativeButton("Skip", (dialog, which) -> {
            dialog.cancel();
            // User skipped, so we proceed without a custom title (pass null)
            String type = intent.getType();
            if (type != null) {
                if (type.startsWith("image/")) {
                    Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                    if (imageUri != null) analyzeImage(imageUri, null);
                } else if ("text/plain".equals(type)) {
                    String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                    if (sharedText != null) analyzeText(sharedText, null);
                }
            }
        });

        builder.show();
    }

    private void analyzeText(String text, @Nullable String customTitle) {
        String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        String textPromptTemplate = "Your SOLE TASK is to analyze the following text and respond ONLY with a single, valid JSON array. The current date is %s. " +
                "Your entire response must be a JSON array, even if only one item is found. If no items are found, return an empty array []. " +
                "Do not include any extra text, explanations, or markdown formatting like ```json. Your response must start with [ and end with ]. " +
                "For each distinct item you find, create a JSON object with these keys: " +
                "1. \"category\": (String) Classify into [\"BILL\", \"RECEIPT\", \"TICKET\", \"TASK\", \"NOTE\"]. " +
                "2. \"title\": (String) A short summary. " +
                "3. \"description\": (String) This is a critical field. If the category is \"NOTE\", you MUST paraphrase the content. For ALL other categories, provide a detailed summary. " +
                "4. \"tags\": (String Array) 1-3 relevant, lowercase tags. " +
                "5. \"amount\": (String) The total value for a BILL or RECEIPT. For others, it MUST be \"N/A\". " +
                "6. \"reminder\": (Object) A nested object with these keys: " +
                "- \"is_reminder\": (Boolean) Must be `true` for BILL, TICKET, and TASK. Must be `false` for RECEIPT and NOTE. " +
                "- \"date\": (String) The date in YYYY-MM-DD format. IMPORTANT: If the category is \"RECEIPT\" and no date is found, you MUST use the current date (%s). For all other types without a date, use \"N/A\". " +
                "- \"time\": (String) The time in 24-hour HH:mm format. If a date exists but no time, use \"09:00\". If no date, use \"N/A\". " +
                "Example: [{\"category\":\"TASK\",\"title\":\"Wish Vishal Happy Birthday\",\"description\":\"Remind me to wish Vishal a happy birthday on August 28th.\",\"tags\":[\"birthday\",\"personal\"],\"amount\":\"N/A\",\"reminder\":{\"is_reminder\":true,\"date\":\"2025-08-28\",\"time\":\"09:00\"}}]";
        String finalPrompt = String.format(textPromptTemplate, currentDate, currentDate);

        if (customTitle != null && !customTitle.isEmpty()) {
            finalPrompt += "\n\nIMPORTANT: You MUST use the following text as the 'title' in your JSON response: \"" + customTitle + "\"";
        }

        Content content = new Content.Builder().addText(finalPrompt + "\n\nHere is the text to analyze:\n" + text).build();
        ListenableFuture<GenerateContentResponse> future = generativeModel.generateContent(content);

        Futures.addCallback(future, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                runOnUiThread(() -> processApiResponse(result.getText()));
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e("ShareActivity", "API call failed for TEXT", t);
                runOnUiThread(() -> showError("API call failed: " + t.getMessage()));
            }
        }, backgroundExecutor);
    }

    private void analyzeImage(Uri uri, @Nullable String customTitle) {
        backgroundExecutor.execute(() -> {
            try {
                Bitmap originalBitmap = uriToBitmap(uri);
                Bitmap bitmap = scaleBitmap(originalBitmap);
                String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
                String imagePromptTemplate = "Your SOLE TASK is to analyze the provided image and respond ONLY with a single, valid JSON array. The current date is %s. " +
                        "Your entire response must be a JSON array, even if only one item is found. If no items are found, return an empty array []. " +
                        "Do not include any extra text, explanations, or markdown formatting like ```json. Your response must start with [ and end with ]. " +
                        "For each distinct item you find in the image, create a JSON object with these keys: " +
                        "1. \"category\": (String) Classify into [\"BILL\", \"RECEIPT\", \"TICKET\", \"TASK\", \"NOTE\"]. " +
                        "2. \"title\": (String) A short summary. " +
                        "3. \"description\": (String) This is a critical field. If the category is \"NOTE\" (like a handwritten list), you MUST paraphrase the content. For ALL other categories, provide a detailed summary of all information. " +
                        "4. \"tags\": (String Array) 1-3 relevant, lowercase tags. " +
                        "5. \"amount\": (String) The total value for a BILL or RECEIPT. For others, it MUST be \"N/A\". " +
                        "6. \"reminder\": (Object) A nested object with these keys: " +
                        "- \"is_reminder\": (Boolean) Must be `true` for BILL, TICKET, and TASK. Must be `false` for RECEIPT and NOTE. " +
                        "- \"date\": (String) The date in YYYY-MM-DD format. IMPORTANT: If the category is \"RECEIPT\" and no date is found, you MUST use the current date (%s). For all other types without a date, use \"N/A\". " +
                        "- \"time\": (String) The time in 24-hour HH:mm format. If a date exists but no time, use \"09:00\". If no date, use \"N/A\". " +
                        "Example for a bill: [{\"category\":\"BILL\",\"title\":\"Pay Electricity Bill\",\"description\":\"Bill for account 12345 from Telangana Power.\",\"tags\":[\"bill\",\"utility\",\"finance\"],\"amount\":\"₹1570.00\",\"reminder\":{\"is_reminder\":true,\"date\":\"2025-09-10\",\"time\":\"09:00\"}}]";
                String finalPrompt = String.format(imagePromptTemplate, currentDate, currentDate);

                if (customTitle != null && !customTitle.isEmpty()) {
                    finalPrompt += "\n\nIMPORTANT: You MUST use the following text as the 'title' in your JSON response: \"" + customTitle + "\"";
                }

                Content content = new Content.Builder().addImage(bitmap).addText(finalPrompt).build();
                ListenableFuture<GenerateContentResponse> future = generativeModel.generateContent(content);

                Futures.addCallback(future, new FutureCallback<GenerateContentResponse>() {
                    @Override
                    public void onSuccess(GenerateContentResponse result) {
                        runOnUiThread(() -> processApiResponse(result.getText()));
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        Log.e("ShareActivity", "API call failed for IMAGE", t);
                        runOnUiThread(() -> showError("API call failed: " + t.getMessage()));
                    }
                }, backgroundExecutor);

            } catch (IOException e) {
                runOnUiThread(() -> showError("Failed to load image: " + e.getMessage()));
            }
        });
    }

    private void processApiResponse(String responseText) {
        Log.d("GEMINI_DEBUG", "RECEIVED RAW RESPONSE: " + responseText);
        if (responseText == null) {
            showError("AI returned an empty response.");
            return;
        }

        int startIndex = responseText.indexOf("[");
        int endIndex = responseText.lastIndexOf("]");
        if (startIndex == -1 || endIndex == -1 || endIndex < startIndex) {
            showError("Could not find valid JSON in the AI response.");
            return;
        }
        String jsonString = responseText.substring(startIndex, endIndex + 1);

        try {
            JSONArray jsonArray = new JSONArray(jsonString);
            if (jsonArray.length() == 0) {
                Toast.makeText(this, "No items were found to save.", Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            ArrayList<ReminderItem> itemsToProcess = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject json = jsonArray.getJSONObject(i);
                ReminderItem newItem = new ReminderItem();
                newItem.category = json.getString("category");
                newItem.title = json.getString("title");
                newItem.description = json.getString("description");
                newItem.tags = json.getJSONArray("tags").toString();
                newItem.amount = parseAmount(json.getString("amount"));

                JSONObject reminderObject = json.getJSONObject("reminder");
                boolean isReminder = reminderObject.getBoolean("is_reminder");
                String dateStr = reminderObject.getString("date");

                if (isReminder && !"N/A".equals(dateStr)) {
                    String timeStr = reminderObject.getString("time");
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
                    Date date = sdf.parse(dateStr + " " + timeStr);
                    newItem.reminderTime = date.getTime();
                    newItem.isActive = true;
                    ReminderManager.setReminder(this, newItem.reminderTime, newItem.title, newItem.description);
                } else if ("RECEIPT".equals(newItem.category)) {
                    if (!"N/A".equals(dateStr)) {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                        Date date = sdf.parse(dateStr);
                        newItem.reminderTime = date.getTime();
                    } else {
                        newItem.reminderTime = System.currentTimeMillis();
                    }
                    newItem.isActive = false;
                } else {
                    newItem.reminderTime = 0;
                    newItem.isActive = false;
                }
                itemsToProcess.add(newItem);
            }
            embedAndSaveItems(itemsToProcess);

        } catch (JSONException | ParseException e) {
            Log.e("JSON_PARSE_ERROR", "Error parsing extracted JSON: " + jsonString, e);
            showError("Could not parse details from the content.");
        }
    }

    private void embedAndSaveItems(ArrayList<ReminderItem> items) {
        if (items.isEmpty()) {
            Toast.makeText(this, "All items saved!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        ReminderItem currentItem = items.remove(0);
        String textToEmbed = currentItem.title + "\n" + currentItem.description;

        embeddingHelper.generateEmbedding(textToEmbed, new EmbeddingHelper.EmbeddingCallback() {
            @Override
            public void onEmbeddingGenerated(List<Float> embedding) {
                currentItem.embedding = EmbeddingHelper.embeddingToString(embedding);
                reminderViewModel.insert(currentItem);
                embedAndSaveItems(items); // Process the next item
            }
            @Override
            public void onError(Throwable t) {
                Log.e("EMBEDDING_ERROR", "Could not generate embedding for '" + currentItem.title + "', saving without it.", t);
                reminderViewModel.insert(currentItem); // Save without embedding on error
                embedAndSaveItems(items); // Process the next item
            }
        });
    }


    private Bitmap scaleBitmap(Bitmap originalBitmap) {
        int originalWidth = originalBitmap.getWidth();
        int originalHeight = originalBitmap.getHeight();
        int targetWidth = 1024;
        if (originalWidth <= targetWidth) {
            return originalBitmap;
        }
        int targetHeight = (int) ((float) originalHeight * ((float) targetWidth / (float) originalWidth));
        return Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true);
    }

    private Bitmap uriToBitmap(Uri uri) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), uri));
        } else {
            return MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
        }
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        new android.os.Handler(getMainLooper()).postDelayed(this::finish, 3000);
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