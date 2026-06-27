package com.tripplanning.platform.client;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.tripplanning.platform.tenant.TenantEntity;
import com.tripplanning.platform.tenant.TenantTier;

@Component
public class CustomFieldClient {

  private final WebClient.Builder webClientBuilder;
  private final String localBaseUrl;
  private final String internalSecret;
  private final boolean useLocalServices;

  public CustomFieldClient(
      Environment environment,
      @Value("${tripplanning.services.customfield-base-url:http://localhost:8084}")
          String localBaseUrl,
      @Value("${tripplanning.services.internal-secret:}") String internalSecret) {
    this.localBaseUrl = localBaseUrl;
    this.internalSecret = internalSecret;
    this.useLocalServices = Arrays.asList(environment.getActiveProfiles()).contains("local");
    this.webClientBuilder =
        WebClient.builder()
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
  }

  public List<CustomFieldDeclarationResponse> list(TenantEntity tenant) {
    return webClient(tenant)
        .get()
        .uri("/internal/tenants/{slug}/custom-fields", tenant.getSlug())
        .header("X-Internal-Secret", internalSecret)
        .retrieve()
        .bodyToMono(new ParameterizedTypeReference<List<CustomFieldDeclarationResponse>>() {})
        .block(Duration.ofSeconds(15));
  }

  public CustomFieldDeclarationResponse create(
      TenantEntity tenant, CreateCustomFieldRequest request) {
    return webClient(tenant)
        .post()
        .uri("/internal/tenants/{slug}/custom-fields", tenant.getSlug())
        .header("X-Internal-Secret", internalSecret)
        .bodyValue(request)
        .retrieve()
        .bodyToMono(CustomFieldDeclarationResponse.class)
        .block(Duration.ofSeconds(15));
  }

  public CustomFieldDeclarationResponse archive(
      TenantEntity tenant, String fieldId, boolean archived) {
    return webClient(tenant)
        .patch()
        .uri("/internal/tenants/{slug}/custom-fields/{fieldId}", tenant.getSlug(), fieldId)
        .header("X-Internal-Secret", internalSecret)
        .bodyValue(new ArchiveCustomFieldRequest(archived))
        .retrieve()
        .bodyToMono(CustomFieldDeclarationResponse.class)
        .block(Duration.ofSeconds(15));
  }

  private WebClient webClient(TenantEntity tenant) {
    return webClientBuilder.clone().baseUrl(resolveBaseUrl(tenant)).build();
  }

  private String resolveBaseUrl(TenantEntity tenant) {
    if (!useLocalServices && tenant.getTier() == TenantTier.ENTERPRISE) {
      return "http://customfield-service." + tenant.getNamespace() + ".svc.cluster.local:8084";
    }
    return localBaseUrl;
  }

  public record CustomFieldDeclarationResponse(
      String id, String name, String type, boolean archived, long createdAt) {}

  public record CreateCustomFieldRequest(String id, String name, String type) {}

  public record ArchiveCustomFieldRequest(boolean archived) {}

  public static RuntimeException wrap(String action, WebClientResponseException e) {
    return new IllegalStateException(
        "Custom field " + action + " failed: " + e.getStatusCode() + " " + e.getResponseBodyAsString(),
        e);
  }
}
