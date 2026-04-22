package com.tripplanning.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tripplanning.auth")
public class AuthProperties {

  /** OAuth 2.0 Web client ID used by Google Identity Services (GIS). */
  private String googleClientId = "";

  /** HMAC secret for application JWTs (at least 256 bits / 32 bytes recommended). */
  private String jwtSecret = "";

  private long jwtExpirationSeconds = 43_200;

  public String getGoogleClientId() {
    return googleClientId;
  }

  public void setGoogleClientId(String googleClientId) {
    this.googleClientId = googleClientId;
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
