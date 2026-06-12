package com.tripplanning.common.tenant;

public final class HostTenantResolver {

  public static final String DEFAULT_ENTERPRISE_HOST_BASE = "enterprise.k8s.tbd-htwg.de";

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
    return resolveSlug(hostHeader, hostBase, DEFAULT_ENTERPRISE_HOST_BASE);
  }

  public static String resolveSlug(String hostHeader, String hostBase, String enterpriseHostBase) {
    if (hostHeader == null || hostHeader.isBlank()) {
      return TenantContext.FREE_SLUG;
    }
    String host = hostHeader.split(":")[0].trim().toLowerCase();
    String base = hostBase == null ? "" : hostBase.trim().toLowerCase();
    if (base.isEmpty() || host.equals(base)) {
      return TenantContext.FREE_SLUG;
    }

    String entBase =
        enterpriseHostBase == null || enterpriseHostBase.isBlank()
            ? DEFAULT_ENTERPRISE_HOST_BASE
            : enterpriseHostBase.trim().toLowerCase();
    String entSuffix = "." + entBase;
    if (host.endsWith(entSuffix)) {
      String slug = host.substring(0, host.length() - entSuffix.length());
      if (!slug.isBlank() && !slug.contains(".")) {
        return slug;
      }
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

  public static boolean isEnterpriseHost(String hostHeader, String enterpriseHostBase) {
    if (hostHeader == null || hostHeader.isBlank()) {
      return false;
    }
    String host = hostHeader.split(":")[0].trim().toLowerCase();
    String entBase =
        enterpriseHostBase == null || enterpriseHostBase.isBlank()
            ? DEFAULT_ENTERPRISE_HOST_BASE
            : enterpriseHostBase.trim().toLowerCase();
    return host.endsWith("." + entBase);
  }
}
