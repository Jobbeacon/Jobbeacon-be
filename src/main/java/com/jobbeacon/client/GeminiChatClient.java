package com.jobbeacon.client;

import com.google.genai.Client;
import com.google.genai.ResponseStream;
import com.google.genai.types.AutomaticFunctionCallingConfig;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.FunctionCallingConfig;
import com.google.genai.types.FunctionCallingConfigMode;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.google.genai.types.Part;
import com.google.genai.types.ThinkingConfig;
import com.google.genai.types.ThinkingLevel;
import com.google.genai.types.Tool;
import com.google.genai.types.ToolConfig;
import com.jobbeacon.exception.GeminiEmptyResponseException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiChatClient {

    private static final String JSON_MIME_TYPE = "application/json";

    @Value("${app.gemini.chat-model}")
    private String chatModel;

    @Value("${app.chat.max-output-tokens:1500}")
    private int maxOutputTokens;

    @Value("${app.chat.thinking-budget:}")
    private String thinkingBudget;

    @Value("${app.chat.thinking-level:}")
    private String thinkingLevel;

    @Value("${app.chat.request-timeout:20s}")
    private Duration requestTimeout;

    @Value("${app.chat.retry.max-retries:1}")
    private int retryMaxRetries;

    @Value("${app.chat.retry.initial-delay-ms:500}")
    private long retryInitialDelayMs;

    @Value("${app.chat.retry.multiplier:2.0}")
    private double retryMultiplier;

    private final GeminiRetryExecutor retryExecutor;

    private GeminiRetryPolicy chatRetryPolicy;

    private ThinkingConfig chatThinkingConfig;

    @PostConstruct
    void init() {
        this.chatRetryPolicy = new GeminiRetryPolicy(retryMaxRetries, retryInitialDelayMs, retryMultiplier, true);
        this.chatThinkingConfig = buildThinkingConfig();
        log.info("(init) chatModel: {}, maxOutputTokens: {}, requestTimeout: {}, thinking: {}",
                chatModel, maxOutputTokens, requestTimeout, chatThinkingConfig);
    }

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

    public GenerateContentResponse generateWithTools(String systemPrompt, List<Content> contents, List<Tool> tools) {
        return generateWithTools(systemPrompt, contents, tools, true);
    }

    public GenerateContentResponse generateWithTools(String systemPrompt, List<Content> contents,
                                                     List<Tool> tools, boolean allowTools) {
        return generateWithTools(systemPrompt, contents, tools, allowTools, null);
    }

    public GenerateContentResponse generateWithTools(String systemPrompt, List<Content> contents,
                                                     List<Tool> tools, boolean allowTools, Instant deadline) {
        if (contents == null || contents.isEmpty()) {
            throw new IllegalArgumentException("Cannot generate content from empty conversation");
        }

        String operation = "generateContent(" + chatModel + ", tools" + (allowTools ? "" : ", mode=NONE") + ")";
        return retryExecutor.execute(operation, chatRetryPolicy, deadline,
                client -> client.models.generateContent(chatModel, contents,
                        buildToolsConfig(systemPrompt, tools, allowTools, deadline)));
    }

    /**
     * Opens a streaming generation with the same config as {@link #generateWithTools}.
     * Retry and key rotation (chat policy, bounded by {@code deadline}) only cover opening the stream:
     * the HTTP response status and the first chunk are read inside the retry executor, so 429/5xx,
     * connection errors, an error event as first chunk or an empty stream are retried there.
     * Errors while iterating later chunks are thrown to the caller as-is.
     * The HTTP timeout ({@code min(request-timeout, deadline)}) bounds the whole stream, not each chunk.
     * The returned stream must be iterated and closed on the same thread.
     */
    public ChatStream streamWithTools(String systemPrompt, List<Content> contents, List<Tool> tools,
                                      boolean allowTools, Instant deadline) {
        if (contents == null || contents.isEmpty()) {
            throw new IllegalArgumentException("Cannot generate content from empty conversation");
        }

        String operation = "generateContentStream(" + chatModel + ", tools" + (allowTools ? "" : ", mode=NONE") + ")";
        return retryExecutor.execute(operation, chatRetryPolicy, deadline,
                client -> openStream(client, contents, buildToolsConfig(systemPrompt, tools, allowTools, deadline)));
    }

    private ChatStream openStream(Client client, List<Content> contents, GenerateContentConfig config) {
        ResponseStream<GenerateContentResponse> stream = client.models.generateContentStream(chatModel, contents, config);
        try {
            Iterator<GenerateContentResponse> iterator = stream.iterator();
            if (!iterator.hasNext()) {
                throw new GeminiEmptyResponseException("Gemini returned an empty stream");
            }
            return new ChatStream(stream, iterator.next());
        } catch (RuntimeException e) {
            closeQuietly(stream);
            throw e;
        }
    }

    private static void closeQuietly(ResponseStream<GenerateContentResponse> stream) {
        try {
            stream.close();
        } catch (RuntimeException e) {
            log.debug("(closeQuietly) failed to close Gemini stream: {}", e.getMessage());
        }
    }

    GeminiRetryPolicy chatRetryPolicy() {
        return chatRetryPolicy;
    }

    GenerateContentConfig buildToolsConfig(String systemPrompt, List<Tool> tools, boolean allowTools,
                                           Instant deadline) {
        GenerateContentConfig.Builder configBuilder = GenerateContentConfig.builder()
                .temperature(0.3f)
                .maxOutputTokens(maxOutputTokens)
                .tools(tools)
                .automaticFunctionCalling(AutomaticFunctionCallingConfig.builder().disable(true))
                .httpOptions(HttpOptions.builder()
                        .timeout(requestTimeoutMs(deadline))
                        .retryOptions(HttpRetryOptions.builder().attempts(1).build())
                        .build());
        if (!allowTools) {
            configBuilder.toolConfig(ToolConfig.builder()
                    .functionCallingConfig(FunctionCallingConfig.builder()
                            .mode(FunctionCallingConfigMode.Known.NONE)));
        }
        if (chatThinkingConfig != null) {
            configBuilder.thinkingConfig(chatThinkingConfig);
        }
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            configBuilder.systemInstruction(Content.fromParts(Part.fromText(systemPrompt)));
        }
        return configBuilder.build();
    }

    private int requestTimeoutMs(Instant deadline) {
        long timeoutMs = requestTimeout.toMillis();
        if (deadline != null) {
            long remainingMs = Duration.between(Instant.now(), deadline).toMillis();
            timeoutMs = Math.max(1, Math.min(timeoutMs, remainingMs));
        }
        return Math.toIntExact(timeoutMs);
    }

    private ThinkingConfig buildThinkingConfig() {
        boolean hasLevel = thinkingLevel != null && !thinkingLevel.isBlank();
        boolean hasBudget = thinkingBudget != null && !thinkingBudget.isBlank();
        if (hasLevel) {
            if (hasBudget) {
                log.warn("(buildThinkingConfig) both app.chat.thinking-level and app.chat.thinking-budget are set; "
                        + "using thinking-level only");
            }
            ThinkingLevel.Known level = ThinkingLevel.Known.valueOf(thinkingLevel.trim().toUpperCase(Locale.ROOT));
            return ThinkingConfig.builder().thinkingLevel(level).build();
        }
        if (hasBudget) {
            return ThinkingConfig.builder().thinkingBudget(Integer.parseInt(thinkingBudget.trim())).build();
        }
        return null;
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

    /**
     * Gemini response stream whose first chunk was already read while opening (see {@link #streamWithTools}).
     * Single-use iterator; not thread-safe. {@link #close()} is idempotent and never throws, but must not be
     * called from another thread while a read is blocked (the SDK reader lock blocks until the read returns).
     */
    public static final class ChatStream implements Iterable<GenerateContentResponse>, AutoCloseable {

        private final ResponseStream<GenerateContentResponse> delegate;

        private GenerateContentResponse firstChunk;

        private boolean iterated;

        private boolean closed;

        private ChatStream(ResponseStream<GenerateContentResponse> delegate, GenerateContentResponse firstChunk) {
            this.delegate = delegate;
            this.firstChunk = firstChunk;
        }

        @Override
        public Iterator<GenerateContentResponse> iterator() {
            if (iterated) {
                throw new IllegalStateException("Gemini stream can only be iterated once");
            }
            iterated = true;
            Iterator<GenerateContentResponse> rest = delegate.iterator();
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return firstChunk != null || (!closed && rest.hasNext());
                }

                @Override
                public GenerateContentResponse next() {
                    if (firstChunk != null) {
                        GenerateContentResponse chunk = firstChunk;
                        firstChunk = null;
                        return chunk;
                    }
                    if (closed) {
                        throw new NoSuchElementException("Gemini stream is closed");
                    }
                    return rest.next();
                }
            };
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            firstChunk = null;
            closeQuietly(delegate);
        }
    }
}
