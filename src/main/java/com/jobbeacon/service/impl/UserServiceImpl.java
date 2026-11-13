package com.jobbeacon.service.impl;

import com.jobbeacon.dto.response.UserResponse;
import com.jobbeacon.entity.User;
import com.jobbeacon.repository.UserRepository;
import com.jobbeacon.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        .role(user.getRole())
        .status(user.getStatus())
        .createdAt(user.getCreatedAt())
        .lastLoginAt(user.getLastLoginAt())
        .build();
  }
}
