package com.jobbeacon.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Trusts identity headers set by Jobbeacon-gateway. Every request must carry the shared
 * {@code X-Internal-Secret} (when configured); otherwise it is rejected with 401.
 * Not a {@code @Component}: registered as a bean in {@code SecurityConfiguration} with servlet
 * auto-registration disabled, so it only runs inside the security filter chain.
 */
@Slf4j
public class GatewayHeaderAuthFilter extends OncePerRequestFilter {

  private static final String ROLE_PREFIX = "ROLE_";
  private static final String DEFAULT_ROLE = "ROLE_USER";

  private final byte[] internalSecret;
  private final RestAuthenticationEntryPoint authenticationEntryPoint;

  public GatewayHeaderAuthFilter(
      final String internalSecret,
      final RestAuthenticationEntryPoint authenticationEntryPoint
  ) {
    this.internalSecret = internalSecret == null || internalSecret.isBlank()
        ? null
        : internalSecret.getBytes(StandardCharsets.UTF_8);
    this.authenticationEntryPoint = authenticationEntryPoint;
    if (this.internalSecret == null) {
      log.warn("(GatewayHeaderAuthFilter) app.internal-secret is empty, internal secret check is disabled");
    }
  }

  @Override
  protected boolean shouldNotFilterAsyncDispatch() {
    return true;
  }

  @Override
  protected boolean shouldNotFilterErrorDispatch() {
    return true;
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain
  ) throws ServletException, IOException {
    if (internalSecret != null && !isSecretValid(request.getHeader(GatewayHeaders.INTERNAL_SECRET))) {
      SecurityContextHolder.clearContext();
      authenticationEntryPoint.commence(
          request,
          response,
          new InsufficientAuthenticationException("Missing or invalid internal secret")
      );
      return;
    }

    final String uid = trimToNull(request.getHeader(GatewayHeaders.USER_ID));
    if (uid != null) {
      final UsernamePasswordAuthenticationToken authentication =
          new UsernamePasswordAuthenticationToken(uid, null, extractAuthorities(request));
      authentication.setDetails(new GatewayPrincipal(
          uid,
          trimToNull(request.getHeader(GatewayHeaders.USER_EMAIL)),
          "true".equalsIgnoreCase(trimToNull(request.getHeader(GatewayHeaders.USER_EMAIL_VERIFIED))),
          decodeName(request.getHeader(GatewayHeaders.USER_NAME)),
          trimToNull(request.getHeader(GatewayHeaders.USER_PICTURE))
      ));
      SecurityContextHolder.getContext().setAuthentication(authentication);
      log.debug("(doFilterInternal) gateway authentication for uid: {}", uid);
    }

    filterChain.doFilter(request, response);
  }

  private boolean isSecretValid(final String provided) {
    if (provided == null) {
      return false;
    }
    return MessageDigest.isEqual(internalSecret, provided.getBytes(StandardCharsets.UTF_8));
  }

  private List<SimpleGrantedAuthority> extractAuthorities(final HttpServletRequest request) {
    final String header = request.getHeader(GatewayHeaders.USER_ROLE);
    if (header == null || header.isBlank()) {
      return List.of(new SimpleGrantedAuthority(DEFAULT_ROLE));
    }
    final List<SimpleGrantedAuthority> authorities = Arrays.stream(header.split(","))
        .map(String::trim)
        .filter(role -> !role.isEmpty())
        .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role.toUpperCase(Locale.ROOT)))
        .distinct()
        .toList();
    return authorities.isEmpty() ? List.of(new SimpleGrantedAuthority(DEFAULT_ROLE)) : authorities;
  }

  private static String decodeName(final String raw) {
    final String value = trimToNull(raw);
    if (value == null) {
      return null;
    }
    try {
      return URLDecoder.decode(value, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException ex) {
      return value;
    }
  }

  private static String trimToNull(final String value) {
    if (value == null) {
      return null;
    }
    final String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
