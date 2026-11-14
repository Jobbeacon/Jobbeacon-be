package com.jobbeacon.security;

/**
 * Identity forwarded by the gateway, stored in {@code Authentication#getDetails()}.
 */
public record GatewayPrincipal(
    String uid,
    String email,
    boolean emailVerified,
    String name,
    String picture
) {
}
