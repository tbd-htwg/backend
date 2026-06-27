package com.tripplanning.platform.infra;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;

import com.tripplanning.platform.config.PlatformProperties;
import com.tripplanning.platform.tenant.TenantTier;

import lombok.extern.slf4j.Slf4j;
import reactor.netty.http.client.HttpClient;

@Slf4j
@Component
public class GithubTenantInfrastructureProvisioner implements TenantInfrastructureProvisioner {

  private final PlatformProperties platformProperties;
  private final WebClient webClient;

  public GithubTenantInfrastructureProvisioner(PlatformProperties platformProperties) {
    this.platformProperties = platformProperties;
    HttpClient httpClient =
        HttpClient.create()
            .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
            .responseTimeout(Duration.ofSeconds(30));
    this.webClient =
        WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
  }

  @Override
  public void triggerStandardTenant(TenantDispatchPayload payload) {
    dispatch(payload, platformProperties.getGithub().getStandardEventType());
  }

  @Override
  public void triggerEnterpriseTenant(TenantDispatchPayload payload) {
    dispatch(payload, platformProperties.getGithub().getEnterpriseEventType());
  }

  @Override
  public void updateEnterpriseTenantResources(String slug, Map<String, Object> resourceProfile) {
    Map<String, Object> clientPayload = new LinkedHashMap<>();
    clientPayload.put("slug", slug);
    clientPayload.put("tier", TenantTier.ENTERPRISE.name());
    clientPayload.put("resourceProfile", resourceProfile);
    dispatch(clientPayload, platformProperties.getGithub().getEnterpriseResourceEventType(), slug, TenantTier.ENTERPRISE);
  }

  private void dispatch(TenantDispatchPayload payload, String eventType) {
    dispatch(buildClientPayload(payload), eventType, payload.slug(), payload.tier());
  }

  private void dispatch(
      Map<String, Object> clientPayload, String eventType, String slug, TenantTier tier) {
    var github = platformProperties.getGithub();
    String url = github.getDispatchUrl();
    String token = github.getDispatchToken();

    if (url == null || url.isBlank() || token == null || token.isBlank()) {
      log.warn(
          "[stub] GitHub dispatch not configured — tier={}, slug={}, displayName={}",
          tier,
          slug,
          clientPayload.getOrDefault("displayName", ""));
      return;
    }

    Map<String, Object> body =
        Map.of("event_type", eventType, "client_payload", clientPayload);

    webClient
        .post()
        .uri(url)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .retrieve()
        .toBodilessEntity()
        .block();

    log.info(
        "Dispatched {} tenant infrastructure event {} for slug={}", tier, eventType, slug);
  }

  static Map<String, Object> buildClientPayload(TenantDispatchPayload payload) {
    // GitHub repository_dispatch permits at most ten top-level client_payload
    // properties. Keep this contract limited to fields consumed by
    // infrastructure/.github/workflows/tenant-provision.yml. Resource names
    // are derived consistently by the workflow and Terraform.
    Map<String, Object> clientPayload = new LinkedHashMap<>();
    clientPayload.put("slug", payload.slug());
    clientPayload.put("displayName", payload.displayName());
    clientPayload.put("tier", payload.tier().name());
    clientPayload.put("identityPlatformTenantId", payload.identityPlatformTenantId());
    if (payload.tier() == TenantTier.ENTERPRISE && payload.imageTag() != null) {
      clientPayload.put("imageTag", payload.imageTag());
    }
    return clientPayload;
  }
}
