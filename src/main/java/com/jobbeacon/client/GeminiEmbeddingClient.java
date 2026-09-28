package com.jobbeacon.client;

import com.google.genai.Client;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class GeminiEmbeddingClient {

    @Value("${app.gemini.api-key:}")
    private String apiKey;

    @Value("${app.gemini.embedding-model:gemini-embedding-2}")
    private String embeddingModel;

    @Value("${app.gemini.embedding-dimension:1536}")
    private int embeddingDimension;

    private Client client;

    @PostConstruct
    void init() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Gemini API key is not configured (app.gemini.api-key). Category classification will be skipped.");
            return;
        }

        this.client = Client.builder().apiKey(apiKey).build();
    }

    public float[] embed(String text, String taskType) {
        if (client == null) {
            throw new IllegalStateException("Gemini API key is not configured (app.gemini.api-key)");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Cannot generate embedding from empty text");
        }

        EmbedContentConfig config = EmbedContentConfig.builder()
                .taskType(taskType)
                .outputDimensionality(embeddingDimension)
                .build();

        EmbedContentResponse response = client.models.embedContent(embeddingModel, text, config);

        List<ContentEmbedding> embeddings = response.embeddings().orElse(List.of());
        if (embeddings.isEmpty()) {
            throw new IllegalStateException("Gemini returned no embeddings");
        }

        List<Float> values = embeddings.get(0).values().orElse(List.of());
        if (values.isEmpty()) {
            throw new IllegalStateException("Gemini returned an empty embedding");
        }

        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }

        return result;
    }
}
