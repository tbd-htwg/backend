package com.tripplanning.auth;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

import org.springframework.stereotype.Component;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

@Component
public class GoogleCredentialVerifier {

  private final GoogleIdTokenVerifier verifier;

  public GoogleCredentialVerifier(AuthProperties authProperties) {
    String clientId = authProperties.getGoogleClientId();
    if (clientId == null || clientId.isBlank()) {
      this.verifier = null;
    } else {
      this.verifier =
          new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
              .setAudience(Collections.singletonList(clientId))
              .build();
    }
  }

  /**
   * @return Google token payload, never null
   * @throws GeneralSecurityException if the credential is invalid
   * @throws IOException on transport errors
   * @throws IllegalStateException if Google client id is not configured
   */
  public GoogleIdToken.Payload verify(String credential) throws GeneralSecurityException, IOException {
    if (verifier == null) {
      throw new IllegalStateException("TRIPPLANNING_AUTH_GOOGLE_CLIENT_ID is not set");
    }
    GoogleIdToken idToken = verifier.verify(credential);
    if (idToken == null) {
      throw new GeneralSecurityException("Invalid Google ID token");
    }
    return idToken.getPayload();
  }
}
