package com.tripplanning.platform.infra;

import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.google.auth.oauth2.GoogleCredentials;
import com.tripplanning.common.auth.AuthProperties;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@ConditionalOnProperty(
    name = "tripplanning.platform.provisioning.use-stubs",
    havingValue = "false")
public class RestIdentityPlatformClient implements IdentityPlatformClient {

  private static final String IDENTITY_TOOLKIT_SCOPE =
      "https://www.googleapis.com/auth/identitytoolkit";

  private final WebClient webClient;
  private final String projectId;

  public RestIdentityPlatformClient(AuthProperties authProperties) {
    this.projectId = authProperties.getFirebaseProjectId();
    if (projectId == null || projectId.isBlank()) {
      throw new IllegalStateException(
          "TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID required when use-stubs=false");
    }
    this.webClient =
        WebClient.builder()
            .baseUrl("https://identitytoolkit.googleapis.com/v2")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
  }

  @Override
  public TenantAuthConfig createTenant(String slug, String displayName) {
    Map<String, Object> body =
        Map.of(
            "displayName", displayName,
            "allowPasswordSignup", true,
            "enableEmailLinkSignin", false);

    Map<?, ?> response =
        webClient
            .post()
            .uri("/projects/{projectId}/tenants", projectId)
            .headers(h -> h.setBearerAuth(accessToken()))
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Map.class)
            .block();

    if (response == null || !response.containsKey("name")) {
      throw new IllegalStateException("Identity Platform tenant creation returned no name");
    }
    String name = String.valueOf(response.get("name"));
    String tenantId = name.substring(name.lastIndexOf('/') + 1);
    log.info("Created Identity Platform tenant {} for slug={}", tenantId, slug);
    enableProviders(tenantId, List.of("google", "password"));
    return new TenantAuthConfig(tenantId, List.of("google", "password"));
  }

  @Override
  public void enableProviders(String identityPlatformTenantId, List<String> providers) {
    for (String provider : providers) {
      if ("google".equals(provider)) {
        patchDefaultSupportedIdp(identityPlatformTenantId, "google.com", true);
      } else if ("password".equals(provider)) {
        patchTenantPasswordSignup(identityPlatformTenantId, true);
      }
    }
  }

  private void patchDefaultSupportedIdp(String tenantId, String idpId, boolean enabled) {
    webClient
        .patch()
        .uri(
            "/projects/{projectId}/defaultSupportedIdpConfigs/{idpId}",
            projectId,
            idpId)
        .headers(h -> h.setBearerAuth(accessToken()))
        .bodyValue(Map.of("enabled", enabled))
        .retrieve()
        .toBodilessEntity()
        .onErrorResume(
            e -> {
              log.warn("Could not patch default IdP {}: {}", idpId, e.getMessage());
              return Mono.empty();
            })
        .block();
  }

  private void patchTenantPasswordSignup(String tenantId, boolean allow) {
    webClient
        .patch()
        .uri("/projects/{projectId}/tenants/{tenantId}", projectId, tenantId)
        .headers(h -> h.setBearerAuth(accessToken()))
        .bodyValue(Map.of("allowPasswordSignup", allow))
        .retrieve()
        .toBodilessEntity()
        .block();
  }

  private String accessToken() {
    try {
      GoogleCredentials credentials =
          GoogleCredentials.getApplicationDefault().createScoped(IDENTITY_TOOLKIT_SCOPE);
      credentials.refreshIfExpired();
      return credentials.getAccessToken().getTokenValue();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to obtain GCP access token for Identity Toolkit", e);
    }
  }
}
