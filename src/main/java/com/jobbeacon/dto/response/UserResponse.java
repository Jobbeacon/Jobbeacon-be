package com.jobbeacon.dto.response;

import com.jobbeacon.constant.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

  private UUID id;

  private String email;

  private String displayName;

  private String avatarUrl;

  private UserStatus status;

  private Instant createdAt;

  private Instant lastLoginAt;
}
