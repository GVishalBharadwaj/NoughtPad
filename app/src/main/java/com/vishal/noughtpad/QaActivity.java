package com.vishal.noughtpad;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.vishal.noughtpad.databinding.ActivityQaBinding;
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class QaActivity extends AppCompatActivity {

    private ActivityQaBinding binding;
    private ReminderViewModel reminderViewModel;
    private EmbeddingHelper embeddingHelper;
    private GenerativeModelFutures generativeModel;
    private QaAdapter adapter;
    private List<QaMessage> messageList;
    private final Executor backgroundExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityQaBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // --- Initialization ---
        messageList = new ArrayList<>();
        adapter = new QaAdapter(messageList);
        binding.recyclerViewQa.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewQa.setAdapter(adapter);

        embeddingHelper = new EmbeddingHelper();
        reminderViewModel = new ViewModelProvider(this).get(ReminderViewModel.class);

        GenerativeModel gm = new GenerativeModel("gemini-1.5-flash-latest", BuildConfig.GEMINI_API_KEY);
        generativeModel = GenerativeModelFutures.from(gm);

        // --- UI Listeners ---
        binding.buttonSend.setOnClickListener(v -> {
            String question = binding.editTextQuestion.getText().toString().trim();
            if (!question.isEmpty()) {
                askQuestion(question);
            }
        });

        addMessage("Hello! Ask me anything about your notes.", QaMessage.SENT_BY_AI);
    }

    private void askQuestion(String question) {
        addMessage(question, QaMessage.SENT_BY_USER);
        binding.editTextQuestion.setText("");

        addMessage("Thinking...", QaMessage.SENT_BY_AI); // Thinking indicator

        reminderViewModel.getAllItems().observe(this, allItems -> {
            if (allItems == null || allItems.isEmpty()) {
                updateLastMessage("I couldn't find any notes to search.");
                return;
            }

            embeddingHelper.generateEmbedding(question, new EmbeddingHelper.EmbeddingCallback() {
                @Override
                public void onEmbeddingGenerated(List<Float> questionEmbedding) {
                    backgroundExecutor.execute(() -> {
                        List<ReminderItem> relevantItems = findRelevantItems(questionEmbedding, allItems, 5);
                        String context = buildContextFromItems(relevantItems);
                        String finalPrompt = "You are a helpful personal data assistant. Answer the user's question based ONLY on the provided context. If the answer isn't in the data, say you can't find the information. Be concise and friendly.\n\n"
                                + "--- CONTEXT ---\n" + context + "\n--- END CONTEXT ---\n\n"
                                + "Question: \"" + question + "\"";
                        generateFinalAnswer(finalPrompt);
                    });
                }
                @Override
                public void onError(Throwable t) {
                    Log.e("QA_EMBEDDING_ERROR", "Failed to generate embedding for question", t);
                    updateLastMessage("Sorry, I couldn't process your question.");
                }
            });
        });
    }

    private void generateFinalAnswer(String finalPrompt) {
        Content content = new Content.Builder().addText(finalPrompt).build();
        ListenableFuture<GenerateContentResponse> future = generativeModel.generateContent(content);
        Futures.addCallback(future, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                updateLastMessage(result.getText());
            }
            @Override
            public void onFailure(Throwable t) {
                Log.e("QA_FINAL_ANSWER_ERROR", "Failed to get final answer", t);
                updateLastMessage("Sorry, there was an error getting the answer.");
            }
        }, backgroundExecutor);
    }

    private List<ReminderItem> findRelevantItems(List<Float> questionEmbedding, List<ReminderItem> allItems, int topK) {
        Map<ReminderItem, Double> similarityScores = new HashMap<>();
        for (ReminderItem item : allItems) {
            if (item.embedding != null && !item.embedding.isEmpty()) {
                List<Float> itemEmbedding = stringToEmbedding(item.embedding);
                double similarity = cosineSimilarity(questionEmbedding, itemEmbedding);
                similarityScores.put(item, similarity);
            }
        }
        List<Map.Entry<ReminderItem, Double>> sortedItems = new ArrayList<>(similarityScores.entrySet());
        Collections.sort(sortedItems, (o1, o2) -> o2.getValue().compareTo(o1.getValue()));
        List<ReminderItem> topItems = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, sortedItems.size()); i++) {
            topItems.add(sortedItems.get(i).getKey());
        }
        return topItems;
    }

    private String buildContextFromItems(List<ReminderItem> items) {
        StringBuilder sb = new StringBuilder();
        for (ReminderItem item : items) {
            sb.append("- Category: ").append(item.category)
                    .append(", Title: ").append(item.title)
                    .append(", Description: ").append(item.description);
            if (item.amount > 0) sb.append(", Amount: ").append(item.amount);
            if (item.reminderTime > 0) {
                sb.append(", Date: ").append(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(item.reminderTime)));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private List<Float> stringToEmbedding(String embeddingStr) {
        List<Float> embedding = new ArrayList<>();
        String[] values = embeddingStr.split(",");
        for (String value : values) {
            embedding.add(Float.parseFloat(value));
        }
        return embedding;
    }

    private double cosineSimilarity(List<Float> v1, List<Float> v2) {
        if (v1 == null || v2 == null || v1.size() != v2.size()) return 0.0;
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < v1.size(); i++) {
            dotProduct += v1.get(i) * v2.get(i);
            normA += Math.pow(v1.get(i), 2);
            normB += Math.pow(v2.get(i), 2);
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private void addMessage(String message, String sentBy) {
        runOnUiThread(() -> {
            messageList.add(new QaMessage(message, sentBy));
            adapter.notifyItemInserted(messageList.size() - 1);
            binding.recyclerViewQa.scrollToPosition(messageList.size() - 1);
        });
    }

    private void updateLastMessage(String message) {
        runOnUiThread(() -> {
            messageList.get(messageList.size() - 1).message = message;
            adapter.notifyItemChanged(messageList.size() - 1);
        });
    }
}