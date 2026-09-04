package com.jobbeacon.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "auth.provider", havingValue = "firebase")
public class FirebaseTokenFilter extends OncePerRequestFilter {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private final FirebaseAuth firebaseAuth;

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain
  ) throws ServletException, IOException {
    // Skip if already authenticated
    if (SecurityContextHolder.getContext().getAuthentication() != null
        && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()
    ) {
      filterChain.doFilter(request, response);
      return;
    }

    final String authHeader = request.getHeader(AUTHORIZATION_HEADER);

    if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
      filterChain.doFilter(request, response);
      return;
    }

    final String token = authHeader.substring(BEARER_PREFIX.length());

    try {
      final FirebaseToken firebaseToken = firebaseAuth.verifyIdToken(token);
      final UsernamePasswordAuthenticationToken authentication = createAuthentication(firebaseToken, request);
      SecurityContextHolder.getContext().setAuthentication(authentication);
      log.debug("Firebase authentication successful for user: {}", firebaseToken.getEmail());
    } catch (FirebaseAuthException e) {
      log.warn("Firebase token verification failed: {}", e.getMessage());

      SecurityContextHolder.clearContext();
    }

    filterChain.doFilter(request, response);
  }

  /**
   * Create Spring Security authentication from Firebase token.
   * Stores FirebaseToken in details for downstream use by UserSyncService.
   */
  private UsernamePasswordAuthenticationToken createAuthentication(
      final FirebaseToken firebaseToken,
      final HttpServletRequest request
  ) {

    final List<SimpleGrantedAuthority> authorities = extractAuthorities(firebaseToken);

    final UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
            firebaseToken.getUid(),
            null,
            authorities
        );

    authentication.setDetails(firebaseToken);

    return authentication;
  }

  //Extract authorities from Firebase Token
  @SuppressWarnings("unchecked")
  private List<SimpleGrantedAuthority> extractAuthorities(final FirebaseToken firebaseToken) {
    final Map<String, Object> claims = firebaseToken.getClaims();
    final Object role = claims.get("role");

    if (role instanceof String) {
      return List.of(new SimpleGrantedAuthority("ROLE_" + ((String) role).toUpperCase()));
    }

    if (role instanceof List) {
      return ((List<String>) role).stream()
          .map(r -> new SimpleGrantedAuthority("ROLE_" + r.toUpperCase()))
          .toList();
    }

    // Default: ROLE_USER
    return List.of(new SimpleGrantedAuthority("ROLE_USER"));
  }
}