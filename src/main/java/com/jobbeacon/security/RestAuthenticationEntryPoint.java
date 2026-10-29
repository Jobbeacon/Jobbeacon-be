package com.jobbeacon.security;

import static com.jobbeacon.constant.JobBeaconConstants.CommonConstants.DEFAULT_LANGUAGE;
import static com.jobbeacon.constant.JobBeaconConstants.CommonConstants.LANGUAGE;
import static com.jobbeacon.constant.JobBeaconConstants.MessageException.DEFAULT_CODE_UNAUTHORIZED;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobbeacon.dto.response.Error;
import com.jobbeacon.dto.response.ResponseGeneral;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Returns 401 with a {@code ResponseGeneral<Error>} body when a protected endpoint is called
 * without a valid token (missing, malformed or expired), so clients can refresh and retry.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final MessageSource messageSource;
  private final ObjectMapper objectMapper;

  @Override
  public void commence(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final AuthenticationException authException
  ) throws IOException {
    log.debug("(commence) unauthenticated request: {} {}", request.getMethod(), request.getRequestURI());
    if (response.isCommitted()) {
      return;
    }

    final HttpStatus status = HttpStatus.UNAUTHORIZED;
    final ResponseGeneral<Error> body = ResponseGeneral.of(
        status.value(),
        status.getReasonPhrase(),
        Error.of(DEFAULT_CODE_UNAUTHORIZED, getMessage(resolveLanguage(request)))
    );

    response.setStatus(status.value());
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), body);
  }

  private String getMessage(final String language) {
    try {
      return messageSource.getMessage(DEFAULT_CODE_UNAUTHORIZED, null, Locale.forLanguageTag(language));
    } catch (Exception ex) {
      return DEFAULT_CODE_UNAUTHORIZED;
    }
  }

  private String resolveLanguage(final HttpServletRequest request) {
    final String header = request.getHeader(LANGUAGE);
    if (header == null || header.isBlank()) {
      return DEFAULT_LANGUAGE;
    }
    final String primary = header.split(",")[0].trim().split(";")[0].trim();
    if (primary.isEmpty()) {
      return DEFAULT_LANGUAGE;
    }
    return primary.split("[-_]")[0].toLowerCase(Locale.ROOT);
  }
}
