package com.tripplanning.tenant;

import java.io.IOException;

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
  private final TenantPlatformClient tenantPlatformClient;

  public TenantContextFilter(
      @Value("${tripplanning.platform.host-base:k8s.tbd-htwg.de}") String hostBase,
      TenantPlatformClient tenantPlatformClient) {
    this.hostBase = hostBase;
    this.tenantPlatformClient = tenantPlatformClient;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      String host =
          HostTenantResolver.effectiveHost(
              request.getHeader("X-Forwarded-Host"), request.getHeader("Host"));
      String slug = HostTenantResolver.resolveSlug(host, hostBase);
      TenantPlatformClient.TenantRuntime runtime = tenantPlatformClient.resolve(slug);
      TenantContextHolder.set(new TenantContext(runtime.slug(), runtime.tier()));

      if (!"free".equals(slug)) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
          String tokenSlug = jwt.getClaimAsString("tenant_slug");
          if (tokenSlug != null && !tokenSlug.isBlank() && !tokenSlug.equals(slug)) {
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
}
