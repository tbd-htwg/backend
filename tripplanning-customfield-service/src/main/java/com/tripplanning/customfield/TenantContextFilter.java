package com.tripplanning.customfield;

import java.io.IOException;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.tripplanning.common.tenant.HostTenantResolver;
import com.tripplanning.common.tenant.TenantContext;
import com.tripplanning.common.tenant.TenantContextHolder;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(20)
public class TenantContextFilter extends OncePerRequestFilter {

  private final String hostBase;
  private final String enterpriseHostBase;

  public TenantContextFilter(
      @Value("${tripplanning.platform.host-base:k8s.tbd-htwg.de}") String hostBase,
      @Value("${tripplanning.platform.enterprise-host-base:enterprise.k8s.tbd-htwg.de}")
          String enterpriseHostBase) {
    this.hostBase = hostBase;
    this.enterpriseHostBase = enterpriseHostBase;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path != null && (path.startsWith("/internal/") || path.startsWith("/actuator/"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      String host =
          HostTenantResolver.effectiveHost(
              request.getHeader("X-Forwarded-Host"), request.getHeader("Host"));
      String slug = HostTenantResolver.resolveSlug(host, hostBase, enterpriseHostBase);
      String tier = HostTenantResolver.tierForSlug(slug, host, enterpriseHostBase);
      TenantContextHolder.set(new TenantContext(slug, tier));

      boolean publicTripFieldsRead =
          "GET".equalsIgnoreCase(request.getMethod())
              && request.getRequestURI() != null
              && request.getRequestURI().matches("/api/v2/trips/\\d+/custom-fields");

      if (!"free".equals(slug) && !publicTripFieldsRead) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
          String tokenSlug = jwt.getClaimAsString("tenant_slug");
          if (tokenSlug == null || tokenSlug.isBlank() || !tokenSlug.equals(slug)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Tenant mismatch");
            return;
          }
        }
      }

      filterChain.doFilter(request, response);
    } finally {
      TenantContextHolder.clear();
    }
  }

  public static String normalizeSlug(String slug) {
    return slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT);
  }
}
