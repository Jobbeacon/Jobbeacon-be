package com.jobbeacon.client;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.jobbeacon.client.GeminiRetryExecutor.GeminiDeadlineExceededException;
import com.jobbeacon.exception.GeminiEmptyResponseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiRetryExecutorTest {

    private static final List<String> MODELS = List.of("best", "second");

    private final GeminiClientPool pool = new GeminiClientPool();
    private final List<String> calls = new ArrayList<>();
    private final Map<String, RuntimeException> failures = new HashMap<>();
    private GeminiRetryExecutor executor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(pool, "apiKeys", "a,b,c");
        ReflectionTestUtils.setField(pool, "requestTimeout", Duration.ofSeconds(1));
        pool.init();
        executor = new GeminiRetryExecutor(pool);
        ReflectionTestUtils.setField(executor, "maxRetries", 1);
        ReflectionTestUtils.setField(executor, "initialDelayMs", 1L);
        ReflectionTestUtils.setField(executor, "multiplier", 1.0);
        ReflectionTestUtils.setField(executor, "totalTimeout", Duration.ofSeconds(30));
    }

    @Test
    void healthyCallUsesTheBestModel() {
        assertThat(run()).isEqualTo("best1");
        assertThat(calls).containsExactly("best1");
    }

    @Test
    void quotaErrorTriesEveryKeyBeforeTheNextModel() {
        fail("best", error(429));

        assertThat(run()).isEqualTo("second1");
        assertThat(calls).containsExactly("best1", "best2", "best3", "second1");
    }

    @Test
    void quickOverloadTriesEveryKeyBeforeTheNextModel() {
        fail("best", error(503));

        assertThat(run()).isEqualTo("second1");
        assertThat(calls).containsExactly("best1", "best2", "best3", "second1");
    }

    @Test
    void exhaustedKeyIsLeftOutOfTheNextCalls() {
        failures.put("best1", error(429));
        run();
        calls.clear();

        assertThat(run()).isEqualTo("best2");
        assertThat(calls).containsExactly("best2");
    }

    @Test
    void bestModelIsUsedAgainAfterTheCooldown() {
        fail("best", error(429));
        run();
        failures.clear();
        assertThat(run()).startsWith("second");

        expireCooldowns();
        assertThat(run()).startsWith("best");
    }

    @Test
    void hangingModelIsLeftForTheNextModelAtOnceAndUsedAgainWithAnotherKey() {
        failures.put("best1", new GenAiIOException("timeout"));
        assertThat(run()).startsWith("second");
        assertThat(calls).hasSize(2);

        assertThat(run()).isEqualTo("best2");
    }

    @Test
    void modelNotAvailableToOneKeyIsTriedWithTheOthers() {
        failures.put("best1", error(404));
        failures.put("best2", new ApiException(400, "INVALID_ARGUMENT", "API key not valid. Please pass a valid API key."));

        assertThat(run()).isEqualTo("best3");
    }

    @Test
    void rejectedRequestGoesToTheNextModelWithoutTryingOtherKeys() {
        fail("best", error(400));

        assertThat(run()).isEqualTo("second1");
        assertThat(calls).containsExactly("best1", "second1");
    }

    @Test
    void unusableAnswerGoesToTheNextModel() {
        fail("best", new GeminiEmptyResponseException("empty"));
        assertThat(run()).isEqualTo("second1");

        fail("best", new IllegalStateException("no text"));
        assertThat(run()).isEqualTo("second1");
    }

    @Test
    void everythingIsTriedAgainWhenAllFailed() {
        fail("best", error(429));
        fail("second", error(503));

        assertThatThrownBy(this::run).isInstanceOf(ApiException.class);
        assertThat(calls).hasSize(12);
    }

    @Test
    void requestRejectedByEveryModelIsNotTriedAgain() {
        fail("best", error(400));
        fail("second", error(400));

        assertThatThrownBy(this::run).isInstanceOf(ApiException.class);
        assertThat(calls).containsExactly("best1", "second1");
    }

    @Test
    void singleModelOnlyChangesTheKey() {
        failures.put("best1", error(429));

        assertThat(executor.execute("op", List.of("best"), this::call)).isEqualTo("best2");
    }

    @Test
    void noRequestIsMadeAfterTheDeadline() {
        assertThatThrownBy(() -> executor.execute("op", MODELS, new GeminiRetryPolicy(5, 1, 1),
                Instant.now().minusSeconds(1), this::call)).isInstanceOf(GeminiDeadlineExceededException.class);
        assertThat(calls).isEmpty();
    }

    private String run() {
        return executor.execute("op", MODELS, this::call);
    }

    private String call(Client client, String model) {
        int key = 0;
        while (pool.get(key) != client) {
            key++;
        }
        String target = model + (key + 1);
        calls.add(target);
        if (failures.containsKey(target)) {
            throw failures.get(target);
        }
        return target;
    }

    private void fail(String model, RuntimeException e) {
        for (int key = 1; key <= pool.size(); key++) {
            failures.put(model + key, e);
        }
    }

    private static ApiException error(int code) {
        return new ApiException(code, "STATUS", "message");
    }

    private void expireCooldowns() {
        ((Map<?, ?>) ReflectionTestUtils.getField(executor, "cooldowns")).clear();
    }
}
