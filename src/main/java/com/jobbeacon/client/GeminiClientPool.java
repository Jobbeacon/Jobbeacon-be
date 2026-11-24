package com.jobbeacon.client;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class GeminiClientPool {

    @Value("${app.gemini.api-key:}")
    private String apiKeys;

    @Value("${app.gemini.request-timeout:30s}")
    private Duration requestTimeout;

    private List<Client> clients = List.of();

    private final AtomicInteger currentIndex = new AtomicInteger();

    @PostConstruct
    void init() {
        List<String> keys = Arrays.stream(apiKeys.split(","))
                .map(String::trim)
                .filter(key -> !key.isEmpty())
                .distinct()
                .toList();
        if (keys.isEmpty()) {
            log.warn("Gemini API key is not configured (app.gemini.api-key). Gemini features will be skipped.");
            return;
        }

        // The SDK waits forever by default: one request Gemini never answers would block the RabbitMQ listener for good.
        HttpOptions httpOptions = HttpOptions.builder()
                .timeout(Math.toIntExact(requestTimeout.toMillis()))
                // GeminiRetryExecutor does the retrying; the SDK would add its own 5 attempts under each of those.
                .retryOptions(HttpRetryOptions.builder().attempts(1).build())
                .build();
        this.clients = keys.stream()
                .map(key -> Client.builder().apiKey(key).httpOptions(httpOptions).build())
                .toList();
        log.info("Loaded {} Gemini API key(s)", clients.size());
    }

    public boolean isConfigured() {
        return !clients.isEmpty();
    }

    public int size() {
        return clients.size();
    }

    public int currentIndex() {
        return currentIndex.get();
    }

    public Client get(int index) {
        return clients.get(index);
    }

    public void rotateFrom(int failedIndex) {
        if (clients.size() <= 1) {
            return;
        }

        int next = (failedIndex + 1) % clients.size();
        if (currentIndex.compareAndSet(failedIndex, next)) {
            log.warn("[Gemini] Rotate API key #{} -> #{}", failedIndex + 1, next + 1);
        }
    }
}
