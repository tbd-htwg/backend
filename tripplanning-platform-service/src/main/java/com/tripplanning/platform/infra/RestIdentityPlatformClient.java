package com.tripplanning.platform.infra;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;

import com.google.auth.oauth2.GoogleCredentials;
import com.tripplanning.common.auth.AuthProperties;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

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
    HttpClient httpClient =
        HttpClient.create()
            .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
            .responseTimeout(Duration.ofSeconds(30));
    this.webClient =
        WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .baseUrl("https://identitytoolkit.googleapis.com/v2")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
  }

  @Override
  public TenantAuthConfig createTenant(String slug, String displayName) {
    // Identity Platform display names are used as the idempotency key when a
    // provisioning retry occurs before the generated tenant ID was persisted.
    // They must therefore be derived from the unique slug, not the mutable and
    // non-unique customer-facing display name.
    String identityDisplayName = identityDisplayName(slug);
    String existingTenantId = findTenantIdByDisplayName(identityDisplayName);
    if (existingTenantId != null) {
      log.info(
          "Reusing Identity Platform tenant {} for slug={}", existingTenantId, slug);
      enableProviders(existingTenantId, List.of("password"));
      return new TenantAuthConfig(existingTenantId, List.of("password"));
    }

    log.info("Creating Identity Platform tenant for slug={} in project={}", slug, projectId);
    Map<String, Object> body =
        Map.of(
            "displayName", identityDisplayName,
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
    return new TenantAuthConfig(tenantId, List.of("password"));
  }

  private String findTenantIdByDisplayName(String displayName) {
    Map<?, ?> response =
        webClient
            .get()
            .uri("/projects/{projectId}/tenants?pageSize=100", projectId)
            .headers(h -> h.setBearerAuth(accessToken()))
            .retrieve()
            .bodyToMono(Map.class)
            .block();
    if (response == null || !(response.get("tenants") instanceof List<?> tenants)) {
      return null;
    }
    return tenants.stream()
        .filter(Map.class::isInstance)
        .map(Map.class::cast)
        .filter(tenant -> displayName.equals(String.valueOf(tenant.get("displayName"))))
        .map(tenant -> String.valueOf(tenant.get("name")))
        .filter(name -> name.contains("/"))
        .map(name -> name.substring(name.lastIndexOf('/') + 1))
        .findFirst()
        .orElse(null);
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
            uriBuilder ->
                uriBuilder
                    .path(
                        "/projects/{projectId}/tenants/{tenantId}/defaultSupportedIdpConfigs/{idpId}")
                    .queryParam("updateMask", "enabled")
                    .build(projectId, tenantId, idpId))
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
        .uri(
            uriBuilder ->
                uriBuilder
                    .path("/projects/{projectId}/tenants/{tenantId}")
                    .queryParam("updateMask", "allowPasswordSignup")
                    .build(projectId, tenantId))
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

  private String identityDisplayName(String slug) {
    String cleaned = slug.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-");
    if (cleaned.isBlank() || !Character.isLetter(cleaned.charAt(0))) {
      cleaned = "t-" + cleaned;
    }
    if (cleaned.length() < 4) {
      cleaned = cleaned + "-tenant";
    }
    return cleaned.substring(0, Math.min(cleaned.length(), 20));
  }
}
