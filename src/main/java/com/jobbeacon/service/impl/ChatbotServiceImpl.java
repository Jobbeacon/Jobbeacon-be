package com.jobbeacon.service.impl;

import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponsePromptFeedback;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Part;
import com.google.genai.types.Tool;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobbeacon.client.GeminiChatClient;
import com.jobbeacon.configuration.ChatStreamConfiguration.ChatStreamExecutor;
import com.jobbeacon.dto.request.ChatRequest;
import com.jobbeacon.dto.response.ChatResponse;
import com.jobbeacon.dto.response.ResponseGeneral;
import com.jobbeacon.exception.ChatException;
import com.jobbeacon.exception.GeminiEmptyResponseException;
import com.jobbeacon.llm.ChatHistoryCompactor;
import com.jobbeacon.llm.ChatSessionLock;
import com.jobbeacon.llm.ChatSessionStore;
import com.jobbeacon.llm.ChatStreamAggregator;
import com.jobbeacon.llm.ChatTurnListener;
import com.jobbeacon.llm.ChatTurnListener.ChatTurnCancelledException;
import com.jobbeacon.llm.ChatbotPrompt;
import com.jobbeacon.llm.SseChatTurnListener;
import com.jobbeacon.llm.tool.LlmToolRegistry;
import com.jobbeacon.llm.tool.ToolContext;
import com.jobbeacon.service.ChatbotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Service
public class ChatbotServiceImpl implements ChatbotService {

    private static final int MAX_TOOL_ROUNDS = 5;

    private static final int MAX_ROUND_ATTEMPTS = 2;

    private static final Duration LOCK_TTL_MARGIN = Duration.ofSeconds(15);

    private static final int HTTP_REQUEST_TIMEOUT = 408;

    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    private static final int HTTP_SERVER_ERROR = 500;

    private static final String ROUND_SEPARATOR = "\n\n";

    private static final String FALLBACK_REPLY =
            "Xin lỗi, mình chưa xử lý được yêu cầu này. Bạn thử diễn đạt lại giúp mình nhé.";

    private final GeminiChatClient geminiChatClient;
    private final LlmToolRegistry toolRegistry;
    private final ChatSessionStore sessionStore;
    private final ChatSessionLock sessionLock;
    private final ChatHistoryCompactor compactor;
    private final ChatStreamExecutor streamExecutor;
    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;
    private final Duration turnDeadline;
    private final Duration sseTimeout;

    public ChatbotServiceImpl(
            GeminiChatClient geminiChatClient,
            LlmToolRegistry toolRegistry,
            ChatSessionStore sessionStore,
            ChatSessionLock sessionLock,
            ChatHistoryCompactor compactor,
            ChatStreamExecutor streamExecutor,
            ObjectMapper objectMapper,
            MessageSource messageSource,
            @Value("${app.chat.turn-deadline:45s}") Duration turnDeadline,
            @Value("${app.chat.stream.sse-timeout:60s}") Duration sseTimeout
    ) {
        this.geminiChatClient = geminiChatClient;
        this.toolRegistry = toolRegistry;
        this.sessionStore = sessionStore;
        this.sessionLock = sessionLock;
        this.compactor = compactor;
        this.streamExecutor = streamExecutor;
        this.objectMapper = objectMapper;
        this.messageSource = messageSource;
        this.turnDeadline = turnDeadline;
        this.sseTimeout = sseTimeout;
    }

    @Override
    public ResponseGeneral<ChatResponse> chat(UUID userId, ChatRequest request) {
        StringBuilder collected = new StringBuilder();
        ChatResponse result = runTurn(userId, request.message(), new ChatTurnListener() {
            @Override
            public void onDelta(String text) {
                collected.append(text);
            }
        });
        return ResponseGeneral.ofSuccess("success", new ChatResponse(collected.toString().trim(), result.toolCalls()));
    }

    @Override
    public ChatResponse runTurn(UUID userId, String message, ChatTurnListener listener) {
        Turn turn = new Turn(userId, listener);
        String lockToken = acquireLock(turn);
        try {
            ChatResponse response = runLocked(turn, message);
            turn.outcome = turn.discarded ? Outcome.DISCARDED : turn.fallback ? Outcome.FALLBACK : Outcome.OK;
            return response;
        } catch (ChatTurnCancelledException exception) {
            turn.outcome = Outcome.CANCELLED;
            throw exception;
        } catch (ChatException exception) {
            turn.outcome = Outcome.ERROR;
            throw exception;
        } catch (RuntimeException exception) {
            if (listener.isCancelled()) {
                turn.outcome = Outcome.CANCELLED;
                throw new ChatTurnCancelledException();
            }
            turn.outcome = Outcome.ERROR;
            throw unavailable(turn, "turn", exception);
        } finally {
            sessionLock.release(userId, lockToken);
            logMetrics(turn);
        }
    }

    @Override
    public SseEmitter stream(UUID userId, String message, Locale locale) {
        String errorMessage = messageSource.getMessage(ChatException.UNAVAILABLE, null, ChatException.UNAVAILABLE,
                locale);
        SseEmitter emitter = new SseEmitter(sseTimeout.toMillis());
        SseChatTurnListener listener = new SseChatTurnListener(emitter, objectMapper);
        try {
            streamExecutor.submit(() -> runStreamTurn(userId, message, listener, errorMessage));
        } catch (RejectedExecutionException exception) {
            log.error("(stream) Chat stream executor rejected the turn: userId={}", userId, exception);
            listener.error(errorMessage);
        }
        return emitter;
    }

    private void runStreamTurn(UUID userId, String message, SseChatTurnListener listener, String errorMessage) {
        try {
            listener.done(runTurn(userId, message, listener));
        } catch (ChatTurnCancelledException exception) {
            listener.complete();
        } catch (ChatException exception) {
            listener.error(errorMessage);
        } catch (RuntimeException exception) {
            log.error("(runStreamTurn) Chat stream failed: userId={}, error={}",
                    userId, exception.getClass().getSimpleName(), exception);
            listener.error(errorMessage);
        }
    }

    @Override
    public void clearSession(UUID userId) {
        sessionStore.delete(userId);
    }

    private String acquireLock(Turn turn) {
        Optional<String> token;
        try {
            token = sessionLock.tryAcquire(turn.userId, turnDeadline.plus(LOCK_TTL_MARGIN));
        } catch (RuntimeException exception) {
            turn.outcome = Outcome.ERROR;
            logMetrics(turn);
            throw unavailable(turn, "lock", exception);
        }
        if (token.isEmpty()) {
            turn.outcome = Outcome.BUSY;
            logMetrics(turn);
            throw ChatException.busy();
        }
        return token.get();
    }

    private ChatResponse runLocked(Turn turn, String message) {
        Instant deadline = Instant.now().plus(turnDeadline);
        String generation = readGeneration(turn);
        List<Content> contents = loadSession(turn);
        int turnStartIndex = contents.size();
        contents.add(Content.builder().role("user").parts(Part.fromText(message)).build());

        String systemPrompt = ChatbotPrompt.systemPrompt();
        List<Tool> tools = List.of(toolRegistry.tool());
        List<ChatResponse.ToolCall> toolCalls = new ArrayList<>();
        for (int round = 0; ; round++) {
            boolean allowTools = round < MAX_TOOL_ROUNDS;
            ChatStreamAggregator aggregator = streamRound(turn, systemPrompt, contents, tools, allowTools, deadline);
            List<FunctionCall> calls = aggregator.functionCalls();
            if (calls.isEmpty() || !allowTools) {
                contents.add(finalContent(turn, aggregator, calls));
                break;
            }

            aggregator.content().ifPresent(contents::add);
            List<Part> results = new ArrayList<>();
            for (FunctionCall call : calls) {
                checkCancelled(turn);
                String name = call.name().orElse("");
                Map<String, Object> args = call.args().orElse(Map.of());
                turn.listener.onToolCall(name, args);
                turn.tools.add(name);
                Map<String, Object> result = toolRegistry.run(new ToolContext(turn.userId), name, args);
                toolCalls.add(new ChatResponse.ToolCall(name, args, !result.containsKey("error")));
                results.add(Part.fromFunctionResponse(name, result));
            }
            contents.add(Content.builder().role("user").parts(results).build());
        }

        checkCancelled(turn);
        List<Content> compacted = compactor.compact(contents, turnStartIndex);
        OptionalInt saved;
        try {
            saved = sessionStore.save(turn.userId, generation, compacted);
        } catch (RuntimeException exception) {
            throw unavailable(turn, "save", exception);
        }
        if (saved.isPresent()) {
            turn.historyTokensEst = saved.getAsInt();
        } else {
            turn.discarded = true;
            log.info("(runLocked) Chat session was cleared during the turn, history not saved: userId={}",
                    turn.userId);
        }
        return new ChatResponse(turn.reply.toString().trim(), toolCalls);
    }

    private String readGeneration(Turn turn) {
        try {
            return sessionStore.generation(turn.userId);
        } catch (RuntimeException exception) {
            throw unavailable(turn, "load", exception);
        }
    }

    private List<Content> loadSession(Turn turn) {
        try {
            return sessionStore.load(turn.userId);
        } catch (RuntimeException exception) {
            throw unavailable(turn, "load", exception);
        }
    }

    /**
     * Streams one round. A round that failed mid-stream before any text reached the listener is retried once
     * with a fresh aggregator; once text was emitted the turn fails.
     */
    private ChatStreamAggregator streamRound(Turn turn, String systemPrompt, List<Content> contents,
                                             List<Tool> tools, boolean allowTools, Instant deadline) {
        for (int attempt = 1; ; attempt++) {
            checkCancelled(turn);
            checkDeadline(turn, deadline);
            if (attempt == 1) {
                turn.rounds++;
            }
            ChatStreamAggregator aggregator = new ChatStreamAggregator(turn::emit);
            turn.roundStarted();

            GeminiChatClient.ChatStream stream;
            try {
                stream = geminiChatClient.streamWithTools(systemPrompt, contents, tools, allowTools, deadline);
            } catch (GeminiEmptyResponseException exception) {
                log.warn("(streamRound) Gemini returned an empty stream: userId={}, round={}",
                        turn.userId, turn.rounds);
                return aggregator;
            } catch (RuntimeException exception) {
                throw unavailable(turn, "open", exception);
            }

            try (stream) {
                for (GenerateContentResponse chunk : stream) {
                    aggregator.accept(chunk);
                    checkCancelled(turn);
                }
                turn.addUsage(aggregator.usageMetadata());
                return aggregator;
            } catch (ChatTurnCancelledException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                checkCancelled(turn);
                if (isRetryableMidStream(exception) && !aggregator.hasEmittedText() && attempt < MAX_ROUND_ATTEMPTS) {
                    log.warn("(streamRound) Gemini stream failed before any text, retry round: userId={}, "
                                    + "round={}, error={}", turn.userId, turn.rounds, describe(exception));
                    continue;
                }
                throw unavailable(turn, "stream", exception);
            }
        }
    }

    private boolean isRetryableMidStream(RuntimeException exception) {
        if (exception instanceof GenAiIOException) {
            return true;
        }
        if (exception instanceof ApiException apiException) {
            int code = apiException.code();
            return code == HTTP_TOO_MANY_REQUESTS || code == HTTP_REQUEST_TIMEOUT || code >= HTTP_SERVER_ERROR;
        }
        return false;
    }

    /**
     * Model content stored for the last round. Function calls in a forced text round have no response,
     * so only the text is kept. An empty or blocked answer always ends with {@link #FALLBACK_REPLY}
     * (after any text of earlier rounds), so the stored history and the streamed text stay the same.
     */
    private Content finalContent(Turn turn, ChatStreamAggregator aggregator, List<FunctionCall> calls) {
        String text = aggregator.text().trim();
        if (!calls.isEmpty()) {
            log.warn("(finalContent) Gemini returned function calls in the final round: userId={}, calls={}",
                    turn.userId, calls.size());
        }
        if (!text.isEmpty()) {
            return calls.isEmpty()
                    ? aggregator.content().orElseGet(() -> textContent(text))
                    : textContent(text);
        }

        log.warn("(finalContent) Gemini returned no text: userId={}, finishReason={}, blockReason={}",
                turn.userId,
                aggregator.finishReason().orElse(null),
                aggregator.promptFeedback().flatMap(GenerateContentResponsePromptFeedback::blockReason).orElse(null));
        turn.fallback = true;
        turn.emit(FALLBACK_REPLY);
        return textContent(FALLBACK_REPLY);
    }

    private Content textContent(String text) {
        return Content.builder().role("model").parts(Part.fromText(text)).build();
    }

    private void checkCancelled(Turn turn) {
        if (turn.listener.isCancelled()) {
            throw new ChatTurnCancelledException();
        }
    }

    private void checkDeadline(Turn turn, Instant deadline) {
        if (!Instant.now().isBefore(deadline)) {
            log.error("(checkDeadline) Chat turn exceeded deadline: userId={}, round={}, deadline={}",
                    turn.userId, turn.rounds, turnDeadline);
            throw ChatException.unavailable();
        }
    }

    private ChatException unavailable(Turn turn, String stage, RuntimeException exception) {
        log.error("(runTurn) Chat turn failed: userId={}, stage={}, round={}, error={}",
                turn.userId, stage, turn.rounds, describe(exception), exception);
        return ChatException.unavailable(exception);
    }

    private String describe(RuntimeException exception) {
        if (exception instanceof ApiException apiException) {
            return "ApiException(code=%d, status=%s)".formatted(apiException.code(), apiException.status());
        }
        return exception.getClass().getSimpleName();
    }

    private void logMetrics(Turn turn) {
        log.info("(runTurn) Chat turn metrics: userId={}, outcome={}, rounds={}, tools={}, promptTokens={}, "
                        + "outputTokens={}, historyTokensEst={}, latencyMs={}",
                turn.userId, turn.outcome.label, turn.rounds, turn.tools, turn.promptTokens, turn.outputTokens,
                turn.historyTokensEst, Duration.ofNanos(System.nanoTime() - turn.startNanos).toMillis());
    }

    private enum Outcome {
        OK("ok"), FALLBACK("fallback"), ERROR("error"), CANCELLED("cancelled"), BUSY("busy"),
        DISCARDED("discarded");

        private final String label;

        Outcome(String label) {
            this.label = label;
        }
    }

    /** Mutable state of one turn; used by a single thread. */
    private static final class Turn {

        private final UUID userId;
        private final ChatTurnListener listener;
        private final long startNanos = System.nanoTime();
        private final StringBuilder reply = new StringBuilder();
        private final List<String> tools = new ArrayList<>();
        private boolean roundHasText;
        private boolean fallback;
        private boolean discarded;
        private int rounds;
        private long promptTokens;
        private long outputTokens;
        private Integer historyTokensEst;
        private Outcome outcome = Outcome.ERROR;

        private Turn(UUID userId, ChatTurnListener listener) {
            this.userId = userId;
            this.listener = listener;
        }

        private void roundStarted() {
            roundHasText = false;
        }

        /** Sends text to the listener, separating text of a new round from text of earlier rounds. */
        private void emit(String text) {
            if (!roundHasText && !reply.isEmpty()) {
                reply.append(ROUND_SEPARATOR);
                listener.onDelta(ROUND_SEPARATOR);
            }
            roundHasText = true;
            reply.append(text);
            listener.onDelta(text);
        }

        private void addUsage(Optional<GenerateContentResponseUsageMetadata> usage) {
            usage.ifPresent(metadata -> {
                promptTokens += metadata.promptTokenCount().orElse(0);
                outputTokens += metadata.candidatesTokenCount().orElse(0)
                        + metadata.thoughtsTokenCount().orElse(0);
            });
        }
    }
}
