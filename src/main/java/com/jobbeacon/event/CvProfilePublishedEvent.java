package com.jobbeacon.event;

import java.util.UUID;

// Published inside the profile save transaction; listeners must use
// @TransactionalEventListener(phase = AFTER_COMMIT) so they only see committed profiles.
public record CvProfilePublishedEvent(UUID userId, UUID cvProfileId) {
}
