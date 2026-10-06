package com.jobbeacon.client;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.jobbeacon.exception.GeminiEmptyResponseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiRetryExecutor {

    private static final int TOO_MANY_REQUESTS = 429;
    private static final int REQUEST_TIMEOUT = 408;
    private static final int SERVER_ERROR = 500;

    private final GeminiClientPool clientPool;

    @Value("${app.gemini.retry.max-retries:3}")
    private int maxRetries;

    @Value("${app.gemini.retry.initial-delay-ms:1000}")
    private long initialDelayMs;

    @Value("${app.gemini.retry.multiplier:2.0}")
    private double multiplier;

    public <T> T execute(String operation, Function<Client, T> call) {
        if (!clientPool.isConfigured()) {
            throw new IllegalStateException("Gemini API key is not configured (app.gemini.api-key)");
        }

        int keyCount = clientPool.size();
        int keyIndex = clientPool.currentIndex();
        RuntimeException lastError = null;
        for (int tried = 0; tried < keyCount; tried++) {
            Client client = clientPool.get(keyIndex);
            try {
                return executeWithRetry(operation + " [key #" + (keyIndex + 1) + "]", () -> call.apply(client));
            } catch (RuntimeException e) {
                if (Thread.currentThread().isInterrupted()) {
                    throw e;
                }
                lastError = e;
                clientPool.rotateFrom(keyIndex);
                keyIndex = (keyIndex + 1) % keyCount;
            }
        }

        if (keyCount > 1) {
            log.error("[Gemini] {} failed on all {} API keys", operation, keyCount);
        }
        throw lastError;
    }

    private <T> T executeWithRetry(String operation, Supplier<T> call) {
        long delayMs = initialDelayMs;
        for (int attempt = 1; ; attempt++) {
            try {
                return call.get();
            } catch (RuntimeException e) {
                String error = describe(e);
                if (!isRetryable(e)) {
                    log.error("[Gemini] {} failed with non-retryable error: {}", operation, error);
                    throw e;
                }
                if (attempt > maxRetries) {
                    log.error("[Gemini] {} failed after {} attempts: {}", operation, attempt, error);
                    throw e;
                }

                log.warn("[Gemini] {} failed (attempt {}/{}): {}. Retry in {} ms",
                        operation, attempt, maxRetries + 1, error, delayMs);
                sleep(delayMs, e);
                delayMs = Math.round(delayMs * multiplier);
            }
        }
    }

    private boolean isRetryable(RuntimeException e) {
        if (e instanceof ApiException apiException) {
            int code = apiException.code();
            return code == TOO_MANY_REQUESTS || code == REQUEST_TIMEOUT || code >= SERVER_ERROR;
        }
        return e instanceof GenAiIOException || e instanceof GeminiEmptyResponseException;
    }

    private String describe(RuntimeException e) {
        if (e instanceof ApiException apiException) {
            return "code=%d, status=%s, message=%s".formatted(
                    apiException.code(), apiException.status(), apiException.message());
        }
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    private void sleep(long delayMs, RuntimeException cause) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw cause;
        }
    }
}
