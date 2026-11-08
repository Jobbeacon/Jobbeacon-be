package com.jobbeacon.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Authenticates service-to-service calls to {@code /internal/**} by a shared token. The granted authority has no
 * {@code ROLE_} prefix, so no Firebase role claim (which becomes {@code ROLE_<claim>}) can ever match it. A blank
 * configured token rejects every internal call rather than accepting an empty header.
 *
 * <p>Not a {@code @Component}: it is added to the security chain only, not registered as a servlet filter too.
 */
public class InternalTokenFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-Internal-Token";
  public static final String AUTHORITY = "INTERNAL_SERVICE";
  static final String PATH_PREFIX = "/internal/";

  private final byte[] expectedToken;

  public InternalTokenFilter(String expectedToken) {
    this.expectedToken = expectedToken == null ? new byte[0] : expectedToken.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith(request.getContextPath() + PATH_PREFIX);
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String token = request.getHeader(HEADER);
    if (expectedToken.length > 0 && token != null
        && MessageDigest.isEqual(expectedToken, token.getBytes(StandardCharsets.UTF_8))) {
      SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
          "internal-service", null, List.of(new SimpleGrantedAuthority(AUTHORITY))));
    }
    filterChain.doFilter(request, response);
  }
}
