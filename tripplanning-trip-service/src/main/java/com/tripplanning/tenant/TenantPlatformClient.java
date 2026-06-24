package com.tripplanning.tenant;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class TenantPlatformClient {

  public record TenantRuntime(
      String slug,
      String tier,
      String dbName,
      String dbUser,
      String dbPassword,
      String searchIndex,
      String gcsBucket,
      String objectPrefix) {}

  private static final Duration CACHE_TTL = Duration.ofMinutes(5);

  private final WebClient webClient;
  private final String internalSecret;
  private final Map<String, CachedRuntime> cache = new ConcurrentHashMap<>();

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
      return new TenantRuntime("free", "FREE", "tripplanning", "tripplanning_app", "", "tripentity", null, "");
    }
    CachedRuntime cached = cache.get(slug);
    if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
      return cached.runtime();
    }
    try {
      TenantRuntime loaded = loadRuntime(slug);
      cache.put(slug, new CachedRuntime(loaded, Instant.now().plus(CACHE_TTL)));
      return loaded;
    } catch (Exception exception) {
      if (internalSecret == null || internalSecret.isBlank()) {
        return devFallback(slug);
      }
      throw new IllegalStateException("Could not resolve runtime for tenant " + slug, exception);
    }
  }

  private TenantRuntime loadRuntime(String slug) {
    Map<?, ?> body =
        webClient
            .get()
            .uri("/internal/tenants/{slug}", slug)
            .header("X-Internal-Secret", internalSecret)
            .retrieve()
            .bodyToMono(Map.class)
            .block(Duration.ofSeconds(15));
    if (body == null) {
      throw new IllegalStateException("Platform returned an empty tenant runtime");
    }
    return new TenantRuntime(
        String.valueOf(body.get("slug")),
        String.valueOf(body.get("tier")),
        String.valueOf(body.get("dbName")),
        stringOrNull(body.get("dbUser")),
        stringOrNull(body.get("dbPassword")),
        String.valueOf(body.get("searchIndex")),
        stringOrNull(body.get("gcsBucket")),
        stringOrNull(body.get("objectPrefix")));
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
    String user = "tripplanning_app_" + slug.replace('-', '_');
    return new TenantRuntime(
        slug, "STANDARD", db, user, "", "tripentity-" + slug, null, "std/" + slug + "/");
  }

  public void evict(String slug) {
    cache.remove(slug);
  }

  private record CachedRuntime(TenantRuntime runtime, Instant expiresAt) {}
}
