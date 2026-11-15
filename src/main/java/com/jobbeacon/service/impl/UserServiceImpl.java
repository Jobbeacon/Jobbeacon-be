package com.jobbeacon.service.impl;

import com.jobbeacon.dto.response.UserResponse;
import com.jobbeacon.entity.User;
import com.jobbeacon.exception.base.UnauthorizedException;
import com.jobbeacon.repository.UserRepository;
import com.jobbeacon.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {
  private final UserRepository userRepository;

  @Override
  @Transactional(readOnly = true)
  public UserResponse toResponse(User user) {

    return UserResponse.builder()
        .id(user.getId())
        .email(user.getEmail())
        .displayName(user.getDisplayName())
        .avatarUrl(user.getAvatarUrl())
        .status(user.getStatus())
        .createdAt(user.getCreatedAt())
        .lastLoginAt(user.getLastLoginAt())
        .build();
  }

  @Override
  @Transactional(readOnly = true)
  public UUID getUserId(final String firebaseUid) {
    return userRepository.findByFirebaseUid(firebaseUid)
        .map(User::getId)
        .orElseThrow(() -> {
          log.warn("(getUserId) no synced user for uid: {}", firebaseUid);
          return new UnauthorizedException();
        });
  }
}
