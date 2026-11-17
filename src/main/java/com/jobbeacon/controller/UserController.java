package com.jobbeacon.controller;

import com.jobbeacon.dto.response.ResponseGeneral;
import com.jobbeacon.dto.response.UserResponse;
import com.jobbeacon.entity.User;
import com.jobbeacon.security.GatewayHeaders;
import com.jobbeacon.security.GatewayPrincipal;
import com.jobbeacon.service.UserService;
import com.jobbeacon.service.UserSyncService;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;
  private final UserSyncService userSyncService;

  /**
   * Creates the user on first sign-in and syncs email, name and avatar from the gateway headers.
   * The frontend calls this right after sign-in; other endpoints only read {@code X-User-Id} and
   * expect the user to exist.
   */
  @GetMapping("/me")
  public ResponseGeneral<UserResponse> getCurrentUser(
      @RequestHeader(GatewayHeaders.USER_ID) String uid,
      @RequestHeader(value = GatewayHeaders.USER_EMAIL, required = false) String email,
      @RequestHeader(value = GatewayHeaders.USER_EMAIL_VERIFIED, required = false) boolean emailVerified,
      @RequestHeader(value = GatewayHeaders.USER_NAME, required = false) String name,
      @RequestHeader(value = GatewayHeaders.USER_PICTURE, required = false) String picture
  ) {
    final User user = userSyncService.syncFromPrincipal(
        new GatewayPrincipal(uid, email, emailVerified, decodeName(name), picture));
    return ResponseGeneral.ofSuccess("Get current user success", userService.toResponse(user));
  }

  /** The gateway form-URL-encodes the name (UTF-8, space as {@code +}). */
  private static String decodeName(final String name) {
    if (name == null || name.isBlank()) {
      return null;
    }
    try {
      return URLDecoder.decode(name, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException ex) {
      return name;
    }
  }
}
