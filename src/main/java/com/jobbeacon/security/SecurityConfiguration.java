package com.jobbeacon.security;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

  private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;

  public SecurityConfiguration(RestAuthenticationEntryPoint restAuthenticationEntryPoint) {
    this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
  }

  @Bean
  public GatewayHeaderAuthFilter gatewayHeaderAuthFilter() {
    return new GatewayHeaderAuthFilter();
  }

  @Bean
  public FilterRegistrationBean<GatewayHeaderAuthFilter> gatewayHeaderAuthFilterRegistration(
      GatewayHeaderAuthFilter gatewayHeaderAuthFilter
  ) {
    FilterRegistrationBean<GatewayHeaderAuthFilter> registration =
        new FilterRegistrationBean<>(gatewayHeaderAuthFilter);
    registration.setEnabled(false);
    return registration;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      GatewayHeaderAuthFilter gatewayHeaderAuthFilter
  ) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(ex -> ex.authenticationEntryPoint(restAuthenticationEntryPoint))
        .authorizeHttpRequests(auth -> auth
            .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
            .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
            .requestMatchers("/actuator/health", "/actuator/info").permitAll()
            .requestMatchers("/api/v1/health").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/v1/jobs", "/api/v1/jobs/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/v1/locations/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/v1/categories").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/v1/companies", "/api/v1/companies/**").permitAll()
            .anyRequest().authenticated())
        .addFilterBefore(gatewayHeaderAuthFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
