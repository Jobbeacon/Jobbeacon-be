package com.jobbeacon.client;

import com.google.genai.Client;
import com.google.genai.errors.GenAiIOException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiClientPoolTest {

    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicInteger requests = new AtomicInteger();
    private final ExecutorService serverThreads = Executors.newCachedThreadPool();
    private HttpServer server;

    @BeforeEach
    void startSilentServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(serverThreads);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.start();
        Client.setDefaultBaseUrls(
                Optional.of("http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort()),
                Optional.empty());
    }

    @AfterEach
    void stopServer() {
        Client.setDefaultBaseUrls(Optional.empty(), Optional.empty());
        release.countDown();
        server.stop(0);
        serverThreads.shutdownNow();
    }

    @Test
    @Timeout(value = 20, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void requestThatIsNeverAnsweredTimesOutAndIsRetried() {
        GeminiClientPool pool = new GeminiClientPool();
        ReflectionTestUtils.setField(pool, "apiKeys", "test-key");
        ReflectionTestUtils.setField(pool, "requestTimeout", Duration.ofMillis(500));
        pool.init();

        GeminiRetryExecutor executor = new GeminiRetryExecutor(pool);
        ReflectionTestUtils.setField(executor, "maxRetries", 1);
        ReflectionTestUtils.setField(executor, "initialDelayMs", 10L);
        ReflectionTestUtils.setField(executor, "multiplier", 2.0);
        ReflectionTestUtils.setField(executor, "totalTimeout", Duration.ofSeconds(15));

        assertThatThrownBy(() -> executor.execute("generateContent", List.of("test-model"),
                (client, model) -> client.models.generateContent(model, "hello", null)))
                .isInstanceOf(GenAiIOException.class);
        assertThat(requests.get()).isEqualTo(2);
    }
}
