package com.tripplanning.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tripplanning.auth")
public class AuthProperties {

  /** Firebase project id used to verify Google Identity Platform ID tokens. */
  private String firebaseProjectId = "";

  /** HMAC secret for application JWTs (at least 256 bits / 32 bytes recommended). */
  private String jwtSecret = "";

  private long jwtExpirationSeconds = 43_200;

  /**
   * Optional shared "test bearer" used by seeders and load-test clients to act as any user via the
   * {@code X-Act-As-User} header. Empty disables the feature; only set in non-production
   * environments via {@code TRIPPLANNING_AUTH_TEST_BEARER_TOKEN}.
   */
  private String testBearerToken = "";

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

  public String getTestBearerToken() {
    return testBearerToken;
  }

  public void setTestBearerToken(String testBearerToken) {
    this.testBearerToken = testBearerToken == null ? "" : testBearerToken;
  }
}
