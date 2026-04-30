package com.tripplanning.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tripplanning.auth")
public class AuthProperties {

  /** Firebase project id used to verify Google Identity Platform ID tokens. */
  private String firebaseProjectId = "";

  /** HMAC secret for application JWTs (at least 256 bits / 32 bytes recommended). */
  private String jwtSecret = "";

  private long jwtExpirationSeconds = 43_200;

  public String getFirebaseProjectId() {
    return firebaseProjectId;
  }

  public void setFirebaseProjectId(String firebaseProjectId) {
    this.firebaseProjectId = firebaseProjectId;
  }

  public String getJwtSecret() {
    return jwtSecret;
  }

  public void setJwtSecret(String jwtSecret) {
    this.jwtSecret = jwtSecret;
  }

  public long getJwtExpirationSeconds() {
    return jwtExpirationSeconds;
  }

  public void setJwtExpirationSeconds(long jwtExpirationSeconds) {
    this.jwtExpirationSeconds = jwtExpirationSeconds;
  }
}
