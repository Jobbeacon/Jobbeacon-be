package com.jobbeacon.security;

import com.jobbeacon.constant.UserRole;
import com.jobbeacon.constant.UserStatus;
import com.jobbeacon.entity.User;
import com.jobbeacon.exception.base.ForbiddenException;
import com.jobbeacon.exception.base.UnauthorizedException;
import com.jobbeacon.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Guards {@code /api/v1/admin/**}: the caller must be an ADMIN in the database (not the
 * {@code X-User-Role} header, which the gateway only sets from the Firebase token and is not
 * trusted for authorization). Missing/unknown {@code X-User-Id} means no session at the gateway.
 */
@Component
@RequiredArgsConstructor
public class AdminAccessInterceptor implements HandlerInterceptor {

  private final UserRepository userRepository;

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (HttpMethod.OPTIONS.matches(request.getMethod())) {
      return true;
    }

    final String uid = request.getHeader(GatewayHeaders.USER_ID);
    if (uid == null || uid.isBlank()) {
      throw new UnauthorizedException();
    }

    final User user = this.userRepository.findByFirebaseUid(uid).orElseThrow(UnauthorizedException::new);
    if (user.getRole() != UserRole.ADMIN || user.getStatus() != UserStatus.ACTIVE) {
      throw new ForbiddenException("admin_required");
    }

    return true;
  }
}
