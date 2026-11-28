package com.jobbeacon.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobbeacon.dto.message.IntegrationEventEnvelope;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Publishes integration events to the shared topic exchange, without an outbox (Jobbeacon-notification D-001):
 * callers publish after commit; this waits for the broker's confirm and retries a few times. An event that still
 * fails is logged at ERROR and dropped; the user backfill runner can resend user state.
 */
@Slf4j
@Component
public class IntegrationEventPublisher {

  static final String PRODUCER = "jobbeacon-be";
  private static final int MAX_ATTEMPTS = 3;
  private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration RETRY_DELAY = Duration.ofMillis(500);

  private final RabbitTemplate rabbitTemplate;
  // Spring's ObjectMapper (global snake_case, ISO instants); the RabbitTemplate's JSON converter uses its own mapper.
  private final ObjectMapper objectMapper;
  private final String exchange;

  public IntegrationEventPublisher(
      final RabbitTemplate rabbitTemplate,
      final ObjectMapper objectMapper,
      @Value("${app.rabbitmq.events.exchange}") final String exchange
  ) {
    this.rabbitTemplate = rabbitTemplate;
    this.objectMapper = objectMapper;
    this.exchange = exchange;
  }

  /** @return true when the broker confirmed the event */
  public boolean publish(final String routingKey, final String eventType, final UUID userId, final Object payload) {
    final IntegrationEventEnvelope<Object> envelope = new IntegrationEventEnvelope<>(
        UUID.randomUUID(), eventType, Instant.now(), PRODUCER, userId, payload);
    final Message message;
    try {
      message = MessageBuilder.withBody(objectMapper.writeValueAsBytes(envelope))
          .setContentType(MediaType.APPLICATION_JSON_VALUE)
          .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
          .setMessageId(envelope.eventId().toString())
          .build();
    } catch (JsonProcessingException e) {
      log.error("(publish) cannot serialize {} for user {}: {}", eventType, userId, e.getMessage());
      return false;
    }

    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        rabbitTemplate.invoke(operations -> {
          operations.send(exchange, routingKey, message);
          operations.waitForConfirmsOrDie(CONFIRM_TIMEOUT.toMillis());
          return null;
        });
        log.debug("(publish) {} {} for user {} confirmed", eventType, envelope.eventId(), userId);
        return true;
      } catch (AmqpException e) {
        log.warn("(publish) {} for user {} attempt {}/{} failed: {}", eventType, userId, attempt, MAX_ATTEMPTS,
            e.getMessage());
        if (attempt < MAX_ATTEMPTS && !sleep()) {
          break;
        }
      }
    }
    log.error("(publish) giving up on {} {} for user {}", eventType, envelope.eventId(), userId);
    return false;
  }

  private static boolean sleep() {
    try {
      Thread.sleep(RETRY_DELAY.toMillis());
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }
}
