package com.jobbeacon.messaging;

import com.jobbeacon.dto.message.UserUpsertedPayload;
import com.jobbeacon.entity.User;
import com.jobbeacon.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * Resends user.upserted for every user, so the notification service gets users that existed before it (or fixes a
 * copy after a lost event). Run once with USER_CONTACT_BACKFILL_ON_STARTUP=true, after the notification service is
 * up (its queue must be bound, or RabbitMQ drops the messages), then turn it off. Repeating it is harmless.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.user-contact.backfill-on-startup", havingValue = "true")
public class UserContactBackfillRunner implements ApplicationRunner {

  private static final int PAGE_SIZE = 500;

  private final UserRepository userRepository;
  private final IntegrationEventPublisher publisher;

  @Override
  public void run(final ApplicationArguments args) {
    int sent = 0;
    int failed = 0;
    Page<User> page;
    int pageNumber = 0;
    do {
      page = userRepository.findAll(PageRequest.of(pageNumber++, PAGE_SIZE, Sort.by("id")));
      for (final User user : page) {
        final boolean ok = publisher.publish(UserContactEventRelay.ROUTING_KEY, UserContactEventRelay.EVENT_TYPE,
            user.getId(), UserUpsertedPayload.of(user));
        if (ok) {
          sent++;
        } else {
          failed++;
        }
      }
    } while (page.hasNext());
    log.info("(run) user contact backfill: {} sent, {} failed", sent, failed);
  }
}
