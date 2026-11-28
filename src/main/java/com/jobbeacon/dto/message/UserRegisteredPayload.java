package com.jobbeacon.dto.message;

import com.jobbeacon.entity.User;
import java.util.UUID;

/** Payload of user.registered: sent once, when the user is created. Not sent by the backfill. */
public record UserRegisteredPayload(UUID userId, String displayName) {

  public static UserRegisteredPayload of(final User user) {
    return new UserRegisteredPayload(user.getId(), user.getDisplayName());
  }
}
