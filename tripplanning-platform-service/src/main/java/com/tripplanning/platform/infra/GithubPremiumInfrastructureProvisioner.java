package com.tripplanning.platform.infra;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.tripplanning.platform.config.PlatformProperties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class GithubPremiumInfrastructureProvisioner implements PremiumInfrastructureProvisioner {

  private final PlatformProperties platformProperties;
  private final WebClient webClient;

  public GithubPremiumInfrastructureProvisioner(PlatformProperties platformProperties) {
    this.platformProperties = platformProperties;
    this.webClient = WebClient.builder().build();
  }

  @Override
  public void triggerPremiumTenant(String slug, String displayName) {
    var github = platformProperties.getGithub();
    String url = github.getDispatchUrl();
    String token = github.getDispatchToken();

    if (url == null || url.isBlank() || token == null || token.isBlank()) {
      log.warn(
          "[stub] Premium GitHub dispatch not configured — slug={}, displayName={}",
          slug,
          displayName);
      return;
    }

    Map<String, Object> payload =
        Map.of(
            "event_type",
            github.getEventType(),
            "client_payload",
            Map.of("slug", slug, "displayName", displayName));

    webClient
        .post()
        .uri(url)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(payload)
        .retrieve()
        .toBodilessEntity()
        .block();

    log.info("Dispatched Premium tenant provisioning for slug={}", slug);
  }
}
