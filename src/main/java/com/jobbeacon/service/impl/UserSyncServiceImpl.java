package com.jobbeacon.service.impl;

import com.google.firebase.auth.FirebaseToken;
import com.jobbeacon.constant.UserRole;
import com.jobbeacon.constant.UserStatus;
import com.jobbeacon.entity.User;
import com.jobbeacon.exception.BusinessException;
import com.jobbeacon.exception.base.ForbiddenException;
import com.jobbeacon.exception.base.UnauthorizedException;
import com.jobbeacon.repository.UserRepository;
import com.jobbeacon.service.UserSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserSyncServiceImpl implements UserSyncService {

  private final UserRepository userRepository;

  /** Emails promoted to ADMIN on sign-in; bootstraps the first administrator. */
  private Set<String> adminEmails = Set.of();

  @Value("${app.admin.emails:}")
  void setAdminEmails(String emails) {
    this.adminEmails = Arrays.stream(emails == null ? new String[0] : emails.split(","))
        .map(email -> email.trim().toLowerCase(Locale.ROOT))
        .filter(email -> !email.isEmpty())
        .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  @Transactional
  public User syncFromFirebaseToken(final FirebaseToken firebaseToken) {
    log.info("(syncFromFirebaseToken) user: {}", firebaseToken.getEmail());

    final String firebaseUid = firebaseToken.getUid();
    final String email = firebaseToken.getEmail();
    final String picture = firebaseToken.getPicture();
    String name = firebaseToken.getName();
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
        if (!firebaseToken.isEmailVerified()) {
          log.warn("Security check failed: Email {} is not verified in Firebase", email);

          throw new BusinessException("EMAIL_NOT_VERIFIED", "Email must be verified to link with an existing account");
        }

        if (existingUser.getFirebaseUid() != null) {
          log.warn("Security check failed: Email {} is already linked to another Firebase UID {}",
              email,
              existingUser.getFirebaseUid()
          );

          throw new BusinessException("EMAIL_ALREADY_IN_USE", "Email already registered with another provider/account");
        }
        // Link this Firebase UID to existing user
        existingUser.setFirebaseUid(firebaseUid);
        log.info("Linked Firebase UID {} to existing user: {}", firebaseUid, email);

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
        .role(this.isBootstrapAdmin(email) ? UserRole.ADMIN : UserRole.USER)
        .lastLoginAt(Instant.now())
        .build();
    newUser = this.userRepository.save(newUser);
    log.info("Created new user from Firebase: {}", newUser.getEmail());

    return newUser;
  }

  @Override
  @Transactional
  public User getCurrentUser() {
    final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) {
      log.warn("Security check failed: Authentication object is null");

      throw new UnauthorizedException("No authenticated user");
    }

    if (auth.getPrincipal() instanceof User user) {
      return this.requireActive(user);
    }

    final Object details = auth.getDetails();
    if (details instanceof FirebaseToken firebaseToken) {
      return this.requireActive(this.syncFromFirebaseToken(firebaseToken));
    }

    log.warn("Security check failed: Authentication object is not of type FirebaseToken");

    throw new UnauthorizedException("No Firebase authentication token found");
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
    if (this.isBootstrapAdmin(user.getEmail()) && user.getRole() != UserRole.ADMIN) {
      user.setRole(UserRole.ADMIN);
      log.info("Promoted bootstrap admin: {}", user.getEmail());
    }
    log.debug("Updated existing user (Firebase): {}", user.getEmail());

    return this.userRepository.save(user);
  }

  private boolean isBootstrapAdmin(final String email) {
    return email != null && this.adminEmails.contains(email.trim().toLowerCase(Locale.ROOT));
  }

  /** Suspended or deleted accounts keep their Firebase login but may not use the API. */
  private User requireActive(final User user) {
    if (user.getStatus() == UserStatus.SUSPENDED) {
      throw new ForbiddenException("user_suspended");
    }
    if (user.getStatus() == UserStatus.DELETED) {
      throw new ForbiddenException("user_deleted");
    }
    return user;
  }
}
