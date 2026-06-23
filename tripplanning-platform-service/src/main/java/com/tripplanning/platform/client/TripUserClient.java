package com.tripplanning.platform.client;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.tripplanning.common.tenant.HostTenantResolver;
import com.tripplanning.platform.tenant.TenantDtos;
import com.tripplanning.platform.tenant.TenantRepository;
import com.tripplanning.platform.tenant.TenantTier;

@Component
public class TripUserClient {

  private final WebClient.Builder webClientBuilder;
  private final String freeBaseUrl;
  private final String internalSecret;
  private final TenantRepository tenantRepository;
  private final String hostBase;
  private final String enterpriseHostBase;

  public TripUserClient(
      @Value("${tripplanning.services.trip-base-url:http://localhost:8080}") String baseUrl,
      @Value("${tripplanning.services.internal-secret:}") String internalSecret,
      @Value("${tripplanning.platform.host-base:k8s.tbd-htwg.de}") String hostBase,
      @Value("${tripplanning.platform.enterprise-host-base:enterprise.k8s.tbd-htwg.de}")
          String enterpriseHostBase,
      TenantRepository tenantRepository) {
    this.freeBaseUrl = baseUrl;
    this.internalSecret = internalSecret;
    this.hostBase = hostBase;
    this.enterpriseHostBase = enterpriseHostBase;
    this.tenantRepository = tenantRepository;
    this.webClientBuilder =
        WebClient.builder().defaultHeader(HttpHeaders.ACCEPT, "application/json");
  }

  public TenantDtos.UserResponseDto provisionIdentity(
      String forwardedHost, String sub, String email, String name, String picture) {
    return post(
        "/internal/users/provision-identity",
        forwardedHost,
        Map.of(
            "sub",
            sub != null ? sub : "",
            "email",
            email,
            "name",
            name != null ? name : "",
            "picture",
            picture != null ? picture : ""));
  }

  public TenantDtos.UserResponseDto provisionDev(String forwardedHost, String email, String name) {
    return post(
        "/internal/users/provision-dev",
        forwardedHost,
        Map.of("email", email, "name", name != null ? name : ""));
  }

  public TenantDtos.UserResponseDto getUser(String forwardedHost, long userId) {
    return webClient()
        .baseUrl(resolveBaseUrl(forwardedHost))
        .build()
        .get()
        .uri("/internal/users/{id}", userId)
        .header("X-Internal-Secret", internalSecret)
        .header("X-Forwarded-Host", forwardedHost != null ? forwardedHost : "")
        .retrieve()
        .bodyToMono(TenantDtos.UserResponseDto.class)
        .block(Duration.ofSeconds(10));
  }

  public List<TenantDtos.TenantUserDto> listUsers(String forwardedHost) {
    return webClient()
        .baseUrl(resolveBaseUrl(forwardedHost))
        .build()
        .get()
        .uri("/internal/users")
        .header("X-Internal-Secret", internalSecret)
        .header("X-Forwarded-Host", forwardedHost != null ? forwardedHost : "")
        .retrieve()
        .bodyToMono(new ParameterizedTypeReference<List<TenantDtos.TenantUserDto>>() {})
        .block(Duration.ofSeconds(10));
  }

  public void deleteUser(String forwardedHost, long userId) {
    webClient()
        .baseUrl(resolveBaseUrl(forwardedHost))
        .build()
        .delete()
        .uri("/internal/users/{id}", userId)
        .header("X-Internal-Secret", internalSecret)
        .header("X-Forwarded-Host", forwardedHost != null ? forwardedHost : "")
        .retrieve()
        .toBodilessEntity()
        .block(Duration.ofSeconds(10));
  }

  private TenantDtos.UserResponseDto post(String path, String forwardedHost, Map<String, String> body) {
    try {
      return webClient()
          .baseUrl(resolveBaseUrl(forwardedHost))
          .build()
          .post()
          .uri(path)
          .header("X-Internal-Secret", internalSecret)
          .header("X-Forwarded-Host", forwardedHost != null ? forwardedHost : "")
          .bodyValue(body)
          .retrieve()
          .bodyToMono(TenantDtos.UserResponseDto.class)
          .block(Duration.ofSeconds(10));
    } catch (WebClientResponseException e) {
      throw new IllegalStateException(
          "Trip user provisioning failed: " + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
    }
  }

  /** Extract host header value from tenant host URL, e.g. https://acme.k8s... → acme.k8s... */
  public static String hostHeaderFromUrl(String hostUrl) {
    if (hostUrl == null || hostUrl.isBlank()) {
      return "";
    }
    String trimmed = hostUrl.trim();
    if (trimmed.startsWith("https://")) {
      trimmed = trimmed.substring("https://".length());
    } else if (trimmed.startsWith("http://")) {
      trimmed = trimmed.substring("http://".length());
    }
    int slash = trimmed.indexOf('/');
    if (slash >= 0) {
      trimmed = trimmed.substring(0, slash);
    }
    return trimmed;
  }

  private WebClient.Builder webClient() {
    return webClientBuilder.clone();
  }

  private String resolveBaseUrl(String forwardedHost) {
    String slug = HostTenantResolver.resolveSlug(forwardedHost, hostBase, enterpriseHostBase);
    if (slug == null || slug.isBlank() || "free".equals(slug)) {
      return freeBaseUrl;
    }
    return tenantRepository
        .findBySlug(slug)
        .map(
            tenant -> {
              if (tenant.getTier() == TenantTier.STANDARD) {
                return "http://trip-service.tripplanning-standard.svc.cluster.local:8080";
              }
              if (tenant.getTier() == TenantTier.ENTERPRISE) {
                return "http://trip-service." + tenant.getNamespace() + ".svc.cluster.local:8080";
              }
              return freeBaseUrl;
            })
        .orElse(freeBaseUrl);
  }
}
