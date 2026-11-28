package com.jobbeacon.event;

import com.jobbeacon.dto.message.UserUpsertedPayload;

// Published inside the user save transaction when a field the notification service copies changed (new user,
// firebase_uid, email, display_name, status). UserContactEventRelay sends it to RabbitMQ after commit.
public record UserContactChangedEvent(UserUpsertedPayload user) {
}
