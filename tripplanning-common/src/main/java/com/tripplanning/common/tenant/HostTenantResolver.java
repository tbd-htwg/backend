package com.tripplanning.common.tenant;

public final class HostTenantResolver {

  private HostTenantResolver() {}

  /**
   * Resolves tenant slug from Host header value, e.g. {@code acme.k8s.tbd-htwg.de} → {@code acme}.
   *
   * @param hostHeader raw Host header (may include port)
   * @param hostBase apex host for the free pool, e.g. {@code k8s.tbd-htwg.de}
   */
  /** Prefer {@code X-Forwarded-Host} when an upstream (e.g. platform-service) forwards the browser host. */
  public static String effectiveHost(String forwardedHost, String hostHeader) {
    if (forwardedHost != null && !forwardedHost.isBlank()) {
      return forwardedHost.trim();
    }
    return hostHeader != null ? hostHeader.trim() : "";
  }

  public static String resolveSlug(String hostHeader, String hostBase) {
    if (hostHeader == null || hostHeader.isBlank()) {
      return TenantContext.FREE_SLUG;
    }
    String host = hostHeader.split(":")[0].trim().toLowerCase();
    String base = hostBase == null ? "" : hostBase.trim().toLowerCase();
    if (base.isEmpty() || host.equals(base)) {
      return TenantContext.FREE_SLUG;
    }
    String suffix = "." + base;
    if (host.endsWith(suffix)) {
      String slug = host.substring(0, host.length() - suffix.length());
      if (!slug.isBlank() && !slug.contains(".")) {
        return slug;
      }
    }
    return TenantContext.FREE_SLUG;
  }
}
