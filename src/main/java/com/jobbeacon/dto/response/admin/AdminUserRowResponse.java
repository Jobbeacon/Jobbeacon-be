package com.jobbeacon.dto.response.admin;

import com.jobbeacon.constant.UserRole;
import com.jobbeacon.constant.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminUserRowResponse(
    UUID id,
    String email,
    String displayName,
    String avatarUrl,
    UserRole role,
    UserStatus status,
    boolean hasCv,
    Instant createdAt,
    Instant lastLoginAt
) {
}
