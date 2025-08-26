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
import androidx.lifecycle.ViewModelProvider;

import com.example.smartc.databinding.ActivityShareBinding;
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
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ShareActivity extends AppCompatActivity {

    private ActivityShareBinding binding;
    private GenerativeModelFutures generativeModel;
    private ReminderViewModel reminderViewModel;
    private final Executor backgroundExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityShareBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Use the secure BuildConfig method to get the API key
        String myApiKey = "AIzaSyA5gi1DNkS1ZddURma6maMoMRsLiK4-WQ0";
        GenerativeModel gm = new GenerativeModel("gemini-2.5-flash", myApiKey);

        generativeModel = GenerativeModelFutures.from(gm);

        // Get the ViewModel to communicate with the database
        reminderViewModel = new ViewModelProvider(this).get(ReminderViewModel.class);

        handleIntent(getIntent());
    }

    private void handleIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) {
            finish();
            return;
        }

        String type = intent.getType();
        if (type != null) {
            if (type.startsWith("image/")) {
                Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (imageUri != null) {
                    analyzeImage(imageUri);
                } else {
                    finish();
                }
            } else if ("text/plain".equals(type)) {
                String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (sharedText != null && !sharedText.isEmpty()) {
                    analyzeText(sharedText);
                } else {
                    finish();
                }
            }
        } else {
            finish();
        }
    }

    private void analyzeText(String text) {
        // Get the current date to provide context to the AI
        String currentDate = new SimpleDateFormat("MMMM dd, yyyy", Locale.US).format(new Date());

        // The master prompt for text, now as a template with a placeholder (%s) for the date
        String textPromptTemplate = "Your SOLE TASK is to analyze the following text and respond with a single, valid JSON object and NOTHING ELSE. Your entire response must be ONLY the JSON object. Do not include any explanatory text, greetings, or markdown formatting like ```json. The current date is %s. " +
                "First, determine the primary category from this list: [\"BILL\", \"RECEIPT\", \"TICKET\", \"TASK\", \"NOTE\"]. A \"TASK\" is a direct command or personal reminder. " +
                "Second, create a JSON object with the following keys: \"category\", \"title\", \"description\", \"tags\", \"amount\", and a nested \"reminder\" object. " +
                "\"description\" is the most important field; use the full text or a detailed summary. " +
                "\"reminder\" is an object containing \"is_reminder\" (boolean, true for BILL/TICKET/TASK), \"date\" (YYYY-MM-DD, calculated from text like 'tomorrow'), and \"time\" (HH:mm, default to \"00:00\" if not found). If is_reminder is false, date and time MUST be \"N/A\". " +
                "Example for a Task: Text input: \"remind me to wish vishal happy birthday on august 28th\". Response: {\"category\":\"TASK\",\"title\":\"Wish Vishal Happy Birthday\",\"description\":\"remind me to wish vishal happy birthday on august 28th\",\"tags\":[\"birthday\",\"personal\"],\"amount\":\"N/A\",\"reminder\":{\"is_reminder\":true,\"date\":\"2025-08-28\",\"time\":\"00:00\"}}";

        // Inject the current date into the prompt
        String finalPrompt = String.format(textPromptTemplate, currentDate);

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
    private void analyzeImage(Uri uri) {
        backgroundExecutor.execute(() -> {
            try {
                Bitmap originalBitmap = uriToBitmap(uri);
                Bitmap bitmap = scaleBitmap(originalBitmap);

                // Get the current date to provide context to the AI
                String currentDate = new SimpleDateFormat("MMMM dd, yyyy", Locale.US).format(new Date());

                // The master prompt for images, now as a template with a placeholder (%s) for the date
                String imagePromptTemplate = "Your SOLE TASK is to analyze the image and respond with a single, valid JSON object and NOTHING ELSE. Your entire response must be ONLY the JSON object. Do not include any explanatory text, greetings, or markdown formatting like ```json. The current date is %s. " +
                        "Analyze the image to determine its \"category\" from [\"BILL\", \"RECEIPT\", \"TICKET\", \"NOTE\"]. " +
                        "- A \"BILL\" is a request for future payment. " +
                        "- A \"RECEIPT\" is a proof of a past payment. " +
                        "- A \"TICKET\" is for an event or travel. " +
                        "- A \"NOTE\" is for everything else. " +
                        "Populate a JSON object with these keys: \"category\", \"title\", \"description\", \"tags\", \"amount\", and a nested \"reminder\" object. " +
                        "\"description\" is the most important field; provide a detailed summary of all information in the image. " +
                        "\"reminder\" is an object containing \"is_reminder\" (boolean), \"date\" (YYYY-MM-DD or \"N/A\"), and \"time\" (HH:mm or \"N/A\", default to \"00:00\" if a date exists but no time). " +
                        "Example for a bill: " +
                        "{\"category\":\"BILL\",\"title\":\"Pay Electricity Bill\",\"description\":\"Bill for account 12345 from Telangana Power.\",\"tags\":[\"bill\",\"utility\",\"finance\"],\"amount\":\"₹1570.00\",\"reminder\":{\"is_reminder\":true,\"date\":\"2025-09-10\",\"time\":\"00:00\"}}";

                // Inject the current date into the prompt
                String finalPrompt = String.format(imagePromptTemplate, currentDate);

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
        Log.d("GEMINI_RESPONSE", "Full API Response: " + responseText);
        if (responseText == null || !responseText.contains("{") || !responseText.contains("}")) {

            showError("AI response was not in the expected format.");

            return;

        }
        try {
            String jsonString = responseText.substring(responseText.indexOf("{"), responseText.lastIndexOf("}") + 1);
            JSONObject json = new JSONObject(jsonString);

            ReminderItem newItem = new ReminderItem();
            newItem.category = json.getString("category");
            newItem.title = json.getString("title");
            newItem.description = json.getString("description");
            newItem.amount = json.getString("amount");
            newItem.tags = json.getJSONArray("tags").toString();

            JSONObject reminderObject = json.getJSONObject("reminder");
            boolean isReminder = reminderObject.getBoolean("is_reminder");

            if (isReminder) {
                String dateStr = reminderObject.getString("date");
                String timeStr = reminderObject.getString("time");

                if (!"N/A".equals(dateStr)) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
                    Date date = sdf.parse(dateStr + " " + timeStr);

                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(date);

                    newItem.reminderTime = calendar.getTimeInMillis();
                    newItem.isActive = true;

                    ReminderManager.setReminder(this, newItem.reminderTime, newItem.title, newItem.description);
                } else {
                    isReminder = false; // Treat as note if date is N/A
                }
            }

            if (!isReminder) {
                newItem.reminderTime = 0;
                newItem.isActive = false;
            }

            reminderViewModel.insert(newItem);
            Toast.makeText(this, newItem.category + " saved!", Toast.LENGTH_LONG).show();

        } catch (JSONException | ParseException e) {
            Log.e("JSON_PARSE_ERROR", "Error parsing API response: " + responseText, e);
            showError("Could not parse details from the content.");
        } finally {
            finish();
        }
    }

    private Bitmap scaleBitmap(Bitmap originalBitmap) {
        int originalWidth = originalBitmap.getWidth();
        int originalHeight = originalBitmap.getHeight();
        int targetWidth = 1024; // Resize for faster API upload
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
        // Handler to close the activity after a delay so the user can see the message
        new android.os.Handler(getMainLooper()).postDelayed(this::finish, 3000);
    }
}