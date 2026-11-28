package com.jobbeacon.messaging;

import com.jobbeacon.event.UserContactChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends user.upserted once the user's transaction committed; a rolled-back change sends nothing. */
@Component
@RequiredArgsConstructor
public class UserContactEventRelay {

  public static final String ROUTING_KEY = "user.upserted";
  public static final String EVENT_TYPE = "USER_UPSERTED";

  private final IntegrationEventPublisher publisher;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onUserContactChanged(final UserContactChangedEvent event) {
    publisher.publish(ROUTING_KEY, EVENT_TYPE, event.user().userId(), event.user());
  }
}
