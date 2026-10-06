package com.jobbeacon.client;

import com.google.genai.Client;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class GeminiClientPool {

    @Value("${app.gemini.api-key:}")
    private String apiKeys;

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

        this.clients = keys.stream()
                .map(key -> Client.builder().apiKey(key).build())
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
