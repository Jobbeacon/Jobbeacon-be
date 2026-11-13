package com.jobbeacon.security;

import com.jobbeacon.entity.User;
import com.jobbeacon.service.UserSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
@RequiredArgsConstructor
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

  private final UserSyncService userSyncService;

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return parameter.hasParameterAnnotation(CurrentUser.class)
        && User.class.isAssignableFrom(parameter.getParameterType());
  }

  @Override
  public Object resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory
  ) {
    Object resolved = webRequest.getAttribute(
        AdminAccessInterceptor.CURRENT_USER_ATTRIBUTE, NativeWebRequest.SCOPE_REQUEST);
    if (resolved instanceof User user) {
      return user;
    }
    return userSyncService.getCurrentUser();
  }
}
