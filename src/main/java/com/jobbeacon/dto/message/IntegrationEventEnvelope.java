package com.jobbeacon.dto.message;

import java.time.Instant;
import java.util.UUID;

/**
 * Common wrapper of every event published to the {@code jobbeacon.events} exchange (same contract as
 * Jobbeacon-notification's EventEnvelope). eventId is generated per event and is the consumer's idempotency key.
 */
public record IntegrationEventEnvelope<T>(
    UUID eventId,
    String eventType,
    Instant occurredAt,
    String producer,
    UUID userId,
    T payload
) {
}
