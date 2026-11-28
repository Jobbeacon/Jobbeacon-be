package com.jobbeacon.event;

import com.jobbeacon.dto.message.UserRegisteredPayload;

/** A user signed in for the first time and was created; published inside the creating transaction. */
public record UserRegisteredEvent(UserRegisteredPayload user) {}
