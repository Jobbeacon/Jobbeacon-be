package com.jobbeacon.client;

import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.jobbeacon.exception.GeminiEmptyResponseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiChatClient {

    private static final String JSON_MIME_TYPE = "application/json";

    @Value("${app.gemini.chat-model:gemini-2.5-flash}")
    private String chatModel;

    private final GeminiRetryExecutor retryExecutor;

    public String generateJson(String systemPrompt, String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalArgumentException("Cannot generate content from empty prompt");
        }

        GenerateContentConfig.Builder configBuilder = GenerateContentConfig.builder()
                .responseMimeType(JSON_MIME_TYPE)
                .temperature(0f);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            configBuilder.systemInstruction(Content.fromParts(Part.fromText(systemPrompt)));
        }

        GenerateContentConfig config = configBuilder.build();
        return retryExecutor.execute("generateContent(" + chatModel + ")",
                client -> extractText(client.models.generateContent(chatModel, userPrompt, config)));
    }

    private String extractText(GenerateContentResponse response) {
        String text = response.text();
        if (text != null && !text.isBlank()) {
            return text;
        }

        FinishReason finishReason = response.finishReason();
        String details = "finishReason=%s, promptFeedback=%s".formatted(
                finishReason, response.promptFeedback().map(Object::toString).orElse(null));
        if (finishReason != null && finishReason.knownEnum() != FinishReason.Known.STOP) {
            throw new IllegalStateException("Gemini returned no text (" + details + ")");
        }
        throw new GeminiEmptyResponseException("Gemini returned an empty response (" + details + ")");
    }
}
