package com.jobbeacon.security;

/**
 * Identity headers attached by Jobbeacon-gateway after verifying the Firebase ID token.
 * Must stay in sync with the gateway's {@code constant/GatewayHeaders}.
 */
public final class GatewayHeaders {

  public static final String USER_ID = "X-User-Id";
  public static final String USER_ROLE = "X-User-Role";
  public static final String USER_EMAIL = "X-User-Email";
  public static final String USER_EMAIL_VERIFIED = "X-User-Email-Verified";
  public static final String USER_NAME = "X-User-Name";
  public static final String USER_PICTURE = "X-User-Picture";
  public static final String INTERNAL_SECRET = "X-Internal-Secret";

  private GatewayHeaders() {
  }
}
