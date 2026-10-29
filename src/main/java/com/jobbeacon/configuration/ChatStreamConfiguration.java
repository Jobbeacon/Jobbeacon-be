package com.jobbeacon.configuration;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class ChatStreamConfiguration {

  public static final String CHAT_STREAM_EXECUTOR = "chatStreamExecutor";

  @Bean(name = CHAT_STREAM_EXECUTOR)
  public ChatStreamExecutor chatStreamExecutor(
      @Value("${app.chat.stream.sse-timeout:60s}") Duration sseTimeout,
      @Value("${app.chat.turn-deadline:45s}") Duration turnDeadline,
      @Value("${app.chat.stream.shutdown-grace:10s}") Duration shutdownGrace
  ) {
    if (sseTimeout.compareTo(turnDeadline) <= 0) {
      log.warn("(chatStreamExecutor) sse-timeout {} is not greater than turn-deadline {}", sseTimeout, turnDeadline);
    }
    ExecutorService delegate = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name("chat-stream-", 0).factory());
    return new ChatStreamExecutor(delegate, shutdownGrace);
  }

  /**
   * Không implement {@link java.util.concurrent.Executor}: Spring Boot 3.3 chỉ tạo
   * {@code applicationTaskExecutor} khi chưa có bean kiểu Executor, và {@code @Async}/MVC async dùng bean đó.
   */
  public static final class ChatStreamExecutor implements AutoCloseable {

    private final ExecutorService delegate;
    private final Duration shutdownGrace;

    ChatStreamExecutor(ExecutorService delegate, Duration shutdownGrace) {
      this.delegate = delegate;
      this.shutdownGrace = shutdownGrace;
    }

    public Future<?> submit(Runnable task) {
      return delegate.submit(task);
    }

    @Override
    public void close() {
      delegate.shutdown();
      try {
        if (!delegate.awaitTermination(shutdownGrace.toMillis(), TimeUnit.MILLISECONDS)) {
          log.warn("(close) chat stream tasks still running after {}, interrupting", shutdownGrace);
          delegate.shutdownNow();
        }
      } catch (InterruptedException e) {
        delegate.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }
}
