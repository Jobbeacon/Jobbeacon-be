package com.jobbeacon.service.impl;

import com.jobbeacon.constant.UserStatus;
import com.jobbeacon.entity.User;
import com.jobbeacon.exception.BusinessException;
import com.jobbeacon.repository.UserRepository;
import com.jobbeacon.security.GatewayPrincipal;
import com.jobbeacon.service.UserSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserSyncServiceImpl implements UserSyncService {

  private final UserRepository userRepository;

  @Override
  @Transactional
  public User syncFromPrincipal(final GatewayPrincipal principal) {
    log.info("(syncFromPrincipal) uid: {}", principal.uid());

    final String firebaseUid = principal.uid();
    final String email = principal.email();
    final String picture = principal.picture();
    String name = principal.name();
    if (name == null || name.isBlank()) {
      name = email != null ? email.split("@")[0] : "User";
    }

    final String finalName = name;

    // 1. Lookup by firebase_uid (exact match)
    final var byFirebaseUid = this.userRepository.findByFirebaseUid(firebaseUid);
    if (byFirebaseUid.isPresent()) {
      return this.updateExistingUser(byFirebaseUid.get(), email, finalName, picture);
    }

    // 2. Fallback: lookup by email (handles Google sign-in after email/password
    // registration)
    // e.g. User registered with email/password (uid=A), then signs in with Google
    // (uid=B, same email)
    if (email != null) {
      final var byEmail = this.userRepository.findByEmail(email);
      if (byEmail.isPresent()) {
        final User existingUser = byEmail.get();

        // Prevent Pre-Authentication Account Takeover
        if (!principal.emailVerified()) {
          log.warn("(syncFromPrincipal) email not verified, refusing to link uid: {}", firebaseUid);

          throw new BusinessException("EMAIL_NOT_VERIFIED", "Email must be verified to link with an existing account");
        }

        if (existingUser.getFirebaseUid() != null) {
          log.warn("(syncFromPrincipal) email already linked to another uid, refusing to link uid: {}", firebaseUid);

          throw new BusinessException("EMAIL_ALREADY_IN_USE", "Email already registered with another provider/account");
        }
        // Link this Firebase UID to existing user
        existingUser.setFirebaseUid(firebaseUid);
        log.info("(syncFromPrincipal) linked uid {} to existing user: {}", firebaseUid, existingUser.getId());

        return this.updateExistingUser(existingUser, email, finalName, picture);
      }
    }

    // 3. Create new user
    User newUser = User.builder()
        .id(UUID.randomUUID())
        .firebaseUid(firebaseUid)
        .email(email != null ? email : firebaseUid + "@firebase.local")
        .displayName(finalName)
        .avatarUrl(picture)
        .status(UserStatus.ACTIVE)
        .lastLoginAt(Instant.now())
        .build();
    newUser = this.userRepository.save(newUser);
    log.info("(syncFromPrincipal) created new user for uid: {}", firebaseUid);

    return newUser;
  }

  private User updateExistingUser(
      final User user,
      final String email,
      final String name,
      final String picture
  ) {
    if (email != null) {
      user.setEmail(email);
    }
    user.setDisplayName(name);
    if (picture != null) {
      user.setAvatarUrl(picture);
    }
    user.setLastLoginAt(Instant.now());
    log.debug("(updateExistingUser) updated user: {}", user.getId());

    return this.userRepository.save(user);
  }
}
