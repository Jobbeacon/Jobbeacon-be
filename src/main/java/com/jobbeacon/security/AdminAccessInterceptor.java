package com.jobbeacon.security;

import com.jobbeacon.constant.UserRole;
import com.jobbeacon.entity.User;
import com.jobbeacon.exception.base.ForbiddenException;
import com.jobbeacon.service.UserSyncService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Guards {@code /api/v1/admin/**}: the caller must be an ADMIN in the database (not a Firebase claim).
 * Inactive accounts are already rejected by {@link UserSyncService#getCurrentUser()}.
 */
@Component
@RequiredArgsConstructor
public class AdminAccessInterceptor implements HandlerInterceptor {

  public static final String CURRENT_USER_ATTRIBUTE = AdminAccessInterceptor.class.getName() + ".user";

  private final UserSyncService userSyncService;

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (HttpMethod.OPTIONS.matches(request.getMethod())) {
      return true;
    }
    User user = userSyncService.getCurrentUser();
    if (user.getRole() != UserRole.ADMIN) {
      throw new ForbiddenException("admin_required");
    }
    request.setAttribute(CURRENT_USER_ATTRIBUTE, user);
    return true;
  }
}
