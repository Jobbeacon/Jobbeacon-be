package com.jobbeacon.security;

public record GatewayPrincipal(
    String uid,
    String email,
    boolean emailVerified,
    String name,
    String picture
) {
}
