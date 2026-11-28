package com.jobbeacon.dto.message;

import com.jobbeacon.entity.User;
import java.util.UUID;

/**
 * Payload of {@code user.upserted}: the user's full current state, not only what changed. The notification service
 * keeps a copy (user_contact) and overwrites it on every event. firebase_uid lets it map the gateway's X-User-Id.
 */
public record UserUpsertedPayload(
    UUID userId,
    String firebaseUid,
    String email,
    String displayName,
    String status
) {

  public static UserUpsertedPayload of(final User user) {
    return new UserUpsertedPayload(user.getId(), user.getFirebaseUid(), user.getEmail(), user.getDisplayName(),
        user.getStatus() != null ? user.getStatus().name() : null);
  }
}
