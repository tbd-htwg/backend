package com.tripplanning.auth;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

@Component
public class FirebaseCredentialVerifier {

  private static final String FIREBASE_JWK_SET_URI =
      "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";

  private final JwtDecoder jwtDecoder;
  private final String projectId;

  public FirebaseCredentialVerifier(AuthProperties authProperties) {
    String configuredProjectId = authProperties.getFirebaseProjectId();
    if (configuredProjectId == null || configuredProjectId.isBlank()) {
      this.jwtDecoder = null;
      this.projectId = "";
      return;
    }

    this.projectId = configuredProjectId.trim();
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(FIREBASE_JWK_SET_URI).build();
    String issuer = "https://securetoken.google.com/" + this.projectId;

    OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);
    OAuth2TokenValidator<Jwt> withAudience =
        token -> {
          String audience = token.getAudience().stream().findFirst().orElse("");
          if (this.projectId.equals(audience)) {
            return OAuth2TokenValidatorResult.success();
          }
          return OAuth2TokenValidatorResult.failure(
              new OAuth2Error(
                  "invalid_token",
                  "Firebase token audience does not match configured project id",
                  null));
        };
    OAuth2TokenValidator<Jwt> withSubject =
        token -> {
          String subject = token.getSubject();
          if (subject != null && !subject.isBlank()) {
            return OAuth2TokenValidatorResult.success();
          }
          return OAuth2TokenValidatorResult.failure(
              new OAuth2Error("invalid_token", "Firebase token subject is missing", null));
        };

    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience, withSubject));
    this.jwtDecoder = decoder;
  }

  /**
   * @return validated Firebase ID token as Spring JWT claims object
   * @throws IllegalStateException if Firebase project id is not configured
   */
  public Jwt verify(String credential) {
    if (jwtDecoder == null) {
      throw new IllegalStateException("TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID is not set");
    }
    return jwtDecoder.decode(credential);
  }
}
