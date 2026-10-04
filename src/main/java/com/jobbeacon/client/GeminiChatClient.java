package com.jobbeacon.client;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GeminiChatClient {

    private static final String JSON_MIME_TYPE = "application/json";

    @Value("${app.gemini.api-key:}")
    private String apiKey;

    @Value("${app.gemini.chat-model:gemini-2.5-flash}")
    private String chatModel;

    private Client client;

    @PostConstruct
    void init() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Gemini API key is not configured (app.gemini.api-key). LLM features will be skipped.");
            return;
        }

        this.client = Client.builder().apiKey(apiKey).build();
    }

    public String generateJson(String prompt) {
        if (client == null) {
            throw new IllegalStateException("Gemini API key is not configured (app.gemini.api-key)");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Cannot generate content from empty prompt");
        }

        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType(JSON_MIME_TYPE)
                .temperature(0f)
                .build();

        GenerateContentResponse response = client.models.generateContent(chatModel, prompt, config);

        String text = response.text();
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("Gemini returned an empty response");
        }

        return text;
    }
}
