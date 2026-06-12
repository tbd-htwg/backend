package com.tripplanning.platform.client;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.tripplanning.platform.tenant.TenantDtos;

@Component
public class TripUserClient {

  private final WebClient webClient;
  private final String internalSecret;

  public TripUserClient(
      @Value("${tripplanning.services.trip-base-url:http://localhost:8080}") String baseUrl,
      @Value("${tripplanning.services.internal-secret:}") String internalSecret) {
    this.internalSecret = internalSecret;
    this.webClient =
        WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.ACCEPT, "application/json")
            .build();
  }

  public TenantDtos.UserResponseDto provisionIdentity(
      String forwardedHost, String sub, String email, String name, String picture) {
    return post(
        "/internal/users/provision-identity",
        forwardedHost,
        Map.of("sub", sub != null ? sub : "", "email", email, "name", name != null ? name : "", "picture", picture != null ? picture : ""));
  }

  public TenantDtos.UserResponseDto provisionDev(String forwardedHost, String email, String name) {
    return post(
        "/internal/users/provision-dev",
        forwardedHost,
        Map.of("email", email, "name", name != null ? name : ""));
  }

  public TenantDtos.UserResponseDto getUser(String forwardedHost, long userId) {
    return webClient
        .get()
        .uri("/internal/users/{id}", userId)
        .header("X-Internal-Secret", internalSecret)
        .header("X-Forwarded-Host", forwardedHost != null ? forwardedHost : "")
        .retrieve()
        .bodyToMono(TenantDtos.UserResponseDto.class)
        .block(Duration.ofSeconds(10));
  }

  private TenantDtos.UserResponseDto post(String path, String forwardedHost, Map<String, String> body) {
    try {
      return webClient
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
}
