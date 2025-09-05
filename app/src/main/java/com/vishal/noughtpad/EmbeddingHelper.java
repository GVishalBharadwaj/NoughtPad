package com.vishal.noughtpad;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.EmbedContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class EmbeddingHelper {

    private final GenerativeModelFutures embeddingModel;
    private final Executor backgroundExecutor = Executors.newSingleThreadExecutor();

    // Interface to get the result back
    public interface EmbeddingCallback {
        void onEmbeddingGenerated(List<Float> embedding);
        void onError(Throwable t);
    }

    public EmbeddingHelper() {
        // Use the dedicated model for embeddings
        GenerativeModel gm = new GenerativeModel("text-embedding-004", BuildConfig.GEMINI_API_KEY);
        embeddingModel = GenerativeModelFutures.from(gm);
    }

    public void generateEmbedding(String textToEmbed, EmbeddingCallback callback) {
        Content content = new Content.Builder().addText(textToEmbed).build();
        ListenableFuture<EmbedContentResponse> future = embeddingModel.embedContent(content);

        Futures.addCallback(future, new FutureCallback<EmbedContentResponse>() {
            @Override
            public void onSuccess(EmbedContentResponse result) {
                if (result != null && result.getEmbedding() != null) {
                    callback.onEmbeddingGenerated(result.getEmbedding().getValues());
                } else {
                    callback.onError(new Exception("Failed to generate embedding, result is null."));
                }
            }
            @Override
            public void onFailure(Throwable t) {
                callback.onError(t);
            }
        }, backgroundExecutor);
    }

    // Helper to convert a list of numbers to a simple string for the database
    public static String embeddingToString(List<Float> embedding) {
        if (embedding == null || embedding.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < embedding.size(); i++) {
            sb.append(embedding.get(i));
            if (i < embedding.size() - 1) {
                sb.append(",");
            }
        }
        return sb.toString();
    }
}