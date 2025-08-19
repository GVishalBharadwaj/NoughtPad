package com.example.smartc;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.smartc.databinding.ActivityShareBinding;
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ShareActivity extends AppCompatActivity {

    private ActivityShareBinding binding;
    private GenerativeModelFutures generativeModel;
    private AppDatabase database;
    private final Executor backgroundExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // ... onCreate is unchanged
        super.onCreate(savedInstanceState);
        binding = ActivityShareBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String myApiKey = "AIzaSyA5gi1DNkS1ZddURma6maMoMRsLiK4-WQ0";
        GenerativeModel gm = new GenerativeModel("gemini-2.5-flash", myApiKey);
        generativeModel = GenerativeModelFutures.from(gm);

        database = AppDatabase.getDatabase(getApplicationContext());
        handleIntent(getIntent());
    }

    @SuppressWarnings("deprecation")
    private void handleIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) {
            finish();
            return;
        }

        String type = intent.getType();
        if (type != null) {
            if (type.startsWith("image/")) {
                Uri imageUri;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
                } else {
                    imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                }
                if (imageUri != null) {
                    analyzeImage(imageUri);
                }
            } else if ("text/plain".equals(type)) {
                // ✅ ACTIVATE THIS CODE BLOCK
                String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (sharedText != null && !sharedText.isEmpty()) {
                    analyzeText(sharedText);
                }
            }
        }
    }

    // ✅ --- NEW METHOD TO HANDLE TEXT ---
    private void analyzeText(String text) {
        // We use the same smart prompt, but we give it text instead of an image
        String prompt = "Analyze this text. Your primary goal is to determine if it's a reminder (task with a date/time) or a simple note. " +
                "Respond ONLY with a valid JSON object. " +
                "The JSON must have a 'type' ('REMINDER' or 'NOTE') and a 'title' (a concise, user-friendly summary of the text). " +
                "If 'type' is 'REMINDER', you MUST also include 'due_date' (YYYY-MM-DD) and 'amount' (if any, otherwise 'N/A'). " +
                "Example for a reminder text: {\"type\": \"REMINDER\", \"title\": \"Call John tomorrow\", \"due_date\": \"2025-08-21\", \"amount\": \"N/A\"}. " +
                "Example for a note: {\"type\": \"NOTE\", \"title\": \"Idea for a new project\"}.";

        Content content = new Content.Builder().addText(prompt + "\n\nHere is the text to analyze:\n" + text).build();
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

    private void analyzeImage(Uri uri) {
        // ... this method is unchanged
        backgroundExecutor.execute(() -> {
            try {
                Bitmap originalBitmap = uriToBitmap(uri);
                Bitmap bitmap = scaleBitmap(originalBitmap);

                String prompt = "Analyze this image. Your primary goal is to determine if it's a reminder (bill, receipt, task with a date) or a simple note. " +
                        "Respond ONLY with a valid JSON object. " +
                        "The JSON must have a 'type' ('REMINDER' or 'NOTE') and a 'title' (a concise, user-friendly summary). " +
                        "If 'type' is 'REMINDER', you MUST also include 'amount' and 'due_date' (YYYY-MM-DD). If amount is not found, use 'N/A'. " +
                        "For a bill, the title should be the payee (e.g., 'Verizon Bill'). " +
                        "For a note, the title should be a summary of the image's content (e.g., 'Shopping List').";

                Content content = new Content.Builder().addImage(bitmap).addText(prompt).build();
                ListenableFuture<GenerateContentResponse> future = generativeModel.generateContent(content);

                Futures.addCallback(future, new FutureCallback<GenerateContentResponse>() {
                    @Override
                    public void onSuccess(GenerateContentResponse result) {
                        runOnUiThread(() -> processApiResponse(result.getText()));
                    }
                    @Override
                    public void onFailure(Throwable t) {
                        Log.e("ShareActivity", "API call failed", t);
                        runOnUiThread(() -> showError("API call failed: " + t.getMessage()));
                    }
                }, backgroundExecutor);

            } catch (IOException e) {
                runOnUiThread(() -> showError("Failed to load image: " + e.getMessage()));
            }
        });
    }

    private void processApiResponse(String responseText) {
        // ... this method is unchanged
        try {
            String jsonString = responseText.substring(responseText.indexOf("{"), responseText.lastIndexOf("}") + 1);
            JSONObject json = new JSONObject(jsonString);

            String type = json.getString("type");
            String title = json.optString("title", "Untitled");

            ReminderItem newItem = new ReminderItem();
            newItem.content = title;

            if ("REMINDER".equals(type)) {
                String amount = json.optString("amount", "");
                String dueDateStr = json.optString("due_date", null);

                if (dueDateStr != null && !dueDateStr.equalsIgnoreCase("N/A")) {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(dateFormat.parse(dueDateStr));
                    calendar.set(Calendar.HOUR_OF_DAY, 9);
                    calendar.set(Calendar.MINUTE, 0);
                    long reminderTime = calendar.getTimeInMillis();

                    ReminderManager.setReminder(this, reminderTime, "Reminder: " + title, "Due today.");

                    newItem.type = "REMINDER";
                    newItem.amount = amount;
                    newItem.reminderTime = reminderTime;
                    newItem.isActive = true;
                    saveItemToDatabase(newItem, "Reminder saved!");
                } else {
                    newItem.type = "NOTE";
                    newItem.details = "Could not determine a due date for this reminder.";
                    saveItemToDatabase(newItem, "Note saved (no date found).");
                }
            } else { // "NOTE"
                newItem.type = "NOTE";
                newItem.details = "Content from shared item.";
                saveItemToDatabase(newItem, "Note saved!");
            }
        } catch (Exception e) {
            Log.e("ShareActivity", "Error processing API response", e);
            runOnUiThread(() -> showError("Could not parse details from image."));
        }
    }

    // ... all other helper methods (saveItemToDatabase, scaleBitmap, etc.) are unchanged
    private void saveItemToDatabase(ReminderItem item, String toastMessage) {
        backgroundExecutor.execute(() -> {
            database.reminderDao().insert(item);
            runOnUiThread(() -> {
                Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show();
                finish();
            });
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
        binding.progressBar.setVisibility(View.GONE);
        binding.statusTextView.setText(message);
        new android.os.Handler(getMainLooper()).postDelayed(this::finish, 3000);
    }
}