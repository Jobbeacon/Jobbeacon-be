package com.jobbeacon.client;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.jobbeacon.exception.GeminiEmptyResponseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Calls Gemini over several models and API keys. Models are ordered best first, and every call starts at the best one:
 * <ul>
 *   <li>the key cannot use the model now (429 quota, 5xx, 408) or at all (401, 403, 404, invalid key): same model with
 *   the next key, and the next model once every key failed;</li>
 *   <li>the model hangs (timeout, network error, or an error that took long to come): next model at once, as waiting
 *   for every key would use up the time of the call;</li>
 *   <li>the model cannot take the request (400) or gives no usable answer: next model;</li>
 *   <li>other errors: thrown.</li>
 * </ul>
 * A model and key that failed are left out of the next calls for a while, then the best model is used again.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiRetryExecutor {

    /** One request with the given client (API key) and model. */
    @FunctionalInterface
    public interface GeminiCall<T> {
        T call(Client client, String model);
    }

    private static final int BAD_REQUEST = 400;
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final int NOT_FOUND = 404;
    private static final int REQUEST_TIMEOUT = 408;
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int SERVER_ERROR = 500;

    private static final Duration COOLDOWN = Duration.ofMinutes(1);
    private static final Duration UNUSABLE_COOLDOWN = Duration.ofHours(1);
    private static final Duration SLOW_FAILURE = Duration.ofSeconds(5);

    private enum Failure { KEY_BUSY, KEY_UNUSABLE, MODEL_HANGS, MODEL_REJECTS, FATAL }

    private record Target(String model, int key) {
    }

    private record Cooldown(Instant until, boolean unusable) {
    }

    private record Pass<T>(T result, RuntimeException error, boolean retryable) {
    }

    private final GeminiClientPool clientPool;

    private final Map<Target, Cooldown> cooldowns = new ConcurrentHashMap<>();

    @Value("${app.gemini.retry.max-retries:3}")
    private int maxRetries;

    @Value("${app.gemini.retry.initial-delay-ms:1000}")
    private long initialDelayMs;

    @Value("${app.gemini.retry.multiplier:2.0}")
    private double multiplier;

    @Value("${app.gemini.total-timeout:90s}")
    private Duration totalTimeout;

    /** Background work: default policy, and no new request once {@code app.gemini.total-timeout} is over. */
    public <T> T execute(String operation, List<String> models, GeminiCall<T> call) {
        return execute(operation, models, new GeminiRetryPolicy(maxRetries, initialDelayMs, multiplier),
                Instant.now().plus(totalTimeout), call);
    }

    public <T> T execute(String operation, List<String> models, GeminiRetryPolicy policy, Instant deadline,
                         GeminiCall<T> call) {
        if (!clientPool.isConfigured()) {
            throw new IllegalStateException("Gemini API key is not configured (app.gemini.api-key)");
        }
        if (models.isEmpty()) {
            throw new IllegalStateException("No Gemini model is configured");
        }

        long delayMs = policy.initialDelayMs();
        for (int round = 1; ; round++) {
            Pass<T> pass = tryTargets(operation, models, deadline, call);
            RuntimeException error = pass.error();
            if (error == null) {
                return pass.result();
            }
            if (!pass.retryable() || round > policy.maxRetries()) {
                log.error("[Gemini] {} failed on every model and key (round {}): {}", operation, round, describe(error));
                throw error;
            }
            checkDeadline(operation, deadline, delayMs, error);
            log.warn("[Gemini] {} failed on every model and key (round {}/{}): {}. Retry in {} ms",
                    operation, round, policy.maxRetries() + 1, describe(error), delayMs);
            sleep(delayMs, error);
            delayMs = Math.round(delayMs * policy.multiplier());
        }
    }

    private <T> Pass<T> tryTargets(String operation, List<String> models, Instant deadline, GeminiCall<T> call) {
        Set<String> skippedModels = new HashSet<>();
        RuntimeException lastError = null;
        boolean retryable = false;
        for (Target target : targets(models)) {
            if (skippedModels.contains(target.model())) {
                continue;
            }
            checkDeadline(operation, deadline, 0, lastError);
            long start = System.nanoTime();
            try {
                T result = call.call(clientPool.get(target.key()), target.model());
                cooldowns.remove(target);
                if (lastError != null) {
                    log.info("[Gemini] {} answered by {} [key #{}]", operation, target.model(), target.key() + 1);
                }
                return new Pass<>(result, null, false);
            } catch (RuntimeException e) {
                Failure failure = classify(e, Duration.ofNanos(System.nanoTime() - start));
                if (failure == Failure.FATAL || Thread.currentThread().isInterrupted()) {
                    throw e;
                }
                log.warn("[Gemini] {} failed on {} [key #{}] ({}): {}",
                        operation, target.model(), target.key() + 1, failure, describe(e));
                lastError = e;
                switch (failure) {
                    case KEY_BUSY -> coolDown(target, COOLDOWN, false);
                    case KEY_UNUSABLE -> coolDown(target, UNUSABLE_COOLDOWN, true);
                    case MODEL_HANGS -> {
                        coolDown(target, COOLDOWN, false);
                        skippedModels.add(target.model());
                    }
                    default -> skippedModels.add(target.model());
                }
                retryable |= failure == Failure.KEY_BUSY || failure == Failure.MODEL_HANGS
                        || e instanceof GeminiEmptyResponseException;
            }
        }
        return new Pass<>(null, lastError, retryable);
    }

    /**
     * Models in order, each with every key from the current one. Targets that failed lately are used only when nothing
     * else is left.
     */
    private List<Target> targets(List<String> models) {
        List<Target> all = new ArrayList<>();
        int firstKey = clientPool.currentIndex();
        for (String model : models) {
            for (int i = 0; i < clientPool.size(); i++) {
                all.add(new Target(model, (firstKey + i) % clientPool.size()));
            }
        }
        Instant now = Instant.now();
        cooldowns.values().removeIf(cooldown -> !cooldown.until().isAfter(now));
        List<Target> ready = filter(all, cooldown -> false);
        if (ready.isEmpty()) {
            ready = filter(all, cooldown -> !cooldown.unusable());
        }
        return ready.isEmpty() ? all : ready;
    }

    private List<Target> filter(List<Target> targets, Predicate<Cooldown> usable) {
        return targets.stream()
                .filter(target -> {
                    Cooldown cooldown = cooldowns.get(target);
                    return cooldown == null || usable.test(cooldown);
                })
                .toList();
    }

    private void coolDown(Target target, Duration duration, boolean unusable) {
        cooldowns.put(target, new Cooldown(Instant.now().plus(duration), unusable));
        clientPool.rotateFrom(target.key());
    }

    private Failure classify(RuntimeException e, Duration elapsed) {
        if (e instanceof GenAiIOException) {
            return Failure.MODEL_HANGS;
        }
        // IllegalStateException: an answer without text, for example one cut off or blocked.
        if (e instanceof GeminiEmptyResponseException || e instanceof IllegalStateException) {
            return Failure.MODEL_REJECTS;
        }
        if (!(e instanceof ApiException apiException)) {
            return Failure.FATAL;
        }
        int code = apiException.code();
        if (code == UNAUTHORIZED || code == FORBIDDEN || code == NOT_FOUND || isInvalidKey(apiException)) {
            return Failure.KEY_UNUSABLE;
        }
        if (code == TOO_MANY_REQUESTS) {
            return Failure.KEY_BUSY;
        }
        if (code == REQUEST_TIMEOUT || code >= SERVER_ERROR) {
            return elapsed.compareTo(SLOW_FAILURE) >= 0 ? Failure.MODEL_HANGS : Failure.KEY_BUSY;
        }
        return code == BAD_REQUEST ? Failure.MODEL_REJECTS : Failure.FATAL;
    }

    /** Gemini answers a wrong key with 400 "API key not valid", not 401. */
    private boolean isInvalidKey(ApiException e) {
        return e.code() == BAD_REQUEST && e.message() != null && e.message().contains("API key");
    }

    private void checkDeadline(String operation, Instant deadline, long upcomingDelayMs, RuntimeException lastError) {
        if (deadline == null) {
            return;
        }
        if (!Instant.now().plusMillis(upcomingDelayMs).isBefore(deadline)) {
            log.warn("[Gemini] {} stopped: deadline exceeded (pendingDelayMs={}, lastError={})",
                    operation, upcomingDelayMs, lastError == null ? null : describe(lastError));
            throw new GeminiDeadlineExceededException(operation, lastError);
        }
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

    public static class GeminiDeadlineExceededException extends RuntimeException {

        public GeminiDeadlineExceededException(String operation, Throwable lastError) {
            super("Gemini " + operation + " exceeded deadline", lastError);
        }
    }
}
