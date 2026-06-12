package com.tripplanning.tenant;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class TenantPlatformClient {

  public record TenantRuntime(
      String slug, String tier, String dbName, String searchIndex, String gcsBucket, String objectPrefix) {}

  private final WebClient webClient;
  private final String internalSecret;
  private final Map<String, TenantRuntime> cache = new ConcurrentHashMap<>();

  public TenantPlatformClient(
      @Value("${tripplanning.platform.base-url:http://localhost:8083}") String baseUrl,
      @Value("${tripplanning.services.internal-secret:}") String internalSecret) {
    this.internalSecret = internalSecret;
    this.webClient =
        WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.ACCEPT, "application/json")
            .build();
  }

  public TenantRuntime resolve(String slug) {
    if (slug == null || slug.isBlank() || "free".equals(slug)) {
      return new TenantRuntime("free", "FREE", "tripplanning", "tripentity", null, "");
    }
    return cache.computeIfAbsent(slug, this::loadRuntime);
  }

  private TenantRuntime loadRuntime(String slug) {
    try {
      Map<?, ?> body =
          webClient
              .get()
              .uri("/internal/tenants/{slug}", slug)
              .header("X-Internal-Secret", internalSecret)
              .retrieve()
              .bodyToMono(Map.class)
              .block(Duration.ofSeconds(5));
      if (body != null) {
        return new TenantRuntime(
            String.valueOf(body.get("slug")),
            String.valueOf(body.get("tier")),
            String.valueOf(body.get("dbName")),
            String.valueOf(body.get("searchIndex")),
            stringOrNull(body.get("gcsBucket")),
            stringOrNull(body.get("objectPrefix")));
      }
    } catch (Exception ignored) {
      // Platform service may be unavailable in local dev — use naming conventions.
    }
    return devFallback(slug);
  }

  private static String stringOrNull(Object value) {
    if (value == null) {
      return null;
    }
    String s = String.valueOf(value);
    return s.isBlank() || "null".equals(s) ? null : s;
  }

  private TenantRuntime devFallback(String slug) {
    String db = "tripplanning_std_" + slug.replace('-', '_');
    return new TenantRuntime(slug, "STANDARD", db, "tripentity-" + slug, null, "std/" + slug + "/");
  }

  public void evict(String slug) {
    cache.remove(slug);
  }
}
