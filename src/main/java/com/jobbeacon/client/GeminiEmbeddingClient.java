package com.jobbeacon.client;

import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import com.jobbeacon.exception.GeminiEmptyResponseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiEmbeddingClient {

    @Value("${app.gemini.embedding-model:gemini-embedding-2}")
    private String embeddingModel;

    @Value("${app.gemini.embedding-dimension:1536}")
    private int embeddingDimension;

    private final GeminiRetryExecutor retryExecutor;

    public float[] embed(String text, String taskType) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Cannot generate embedding from empty text");
        }

        EmbedContentConfig config = EmbedContentConfig.builder()
                .taskType(taskType)
                .outputDimensionality(embeddingDimension)
                .build();

        // Only the key changes on failure: vectors of different models cannot be compared.
        List<Float> values = retryExecutor.execute("embedContent", List.of(embeddingModel),
                (client, model) -> extractValues(client.models.embedContent(model, text, config)));

        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }

        return result;
    }

    private List<Float> extractValues(EmbedContentResponse response) {
        List<ContentEmbedding> embeddings = response.embeddings().orElse(List.of());
        if (embeddings.isEmpty()) {
            throw new GeminiEmptyResponseException("Gemini returned no embeddings");
        }

        List<Float> values = embeddings.get(0).values().orElse(List.of());
        if (values.isEmpty()) {
            throw new GeminiEmptyResponseException("Gemini returned an empty embedding");
        }

        return values;
    }
}
