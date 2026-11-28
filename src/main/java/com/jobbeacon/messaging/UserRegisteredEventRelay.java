package com.jobbeacon.messaging;

import com.jobbeacon.event.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends user.registered once the new user's transaction committed; the notification service welcomes the user. */
@Component
@RequiredArgsConstructor
public class UserRegisteredEventRelay {

  public static final String ROUTING_KEY = "user.registered";
  public static final String EVENT_TYPE = "USER_REGISTERED";

  private final IntegrationEventPublisher publisher;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onUserRegistered(final UserRegisteredEvent event) {
    publisher.publish(ROUTING_KEY, EVENT_TYPE, event.user().userId(), event.user());
  }
}
