package com.tripplanning.social;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
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
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      String host =
          HostTenantResolver.effectiveHost(
              request.getHeader("X-Forwarded-Host"), request.getHeader("Host"));
      String slug = HostTenantResolver.resolveSlug(host, hostBase, enterpriseHostBase);
      String tier = tierForHost(slug, host, enterpriseHostBase);
      TenantContextHolder.set(new TenantContext(slug, tier));
      filterChain.doFilter(request, response);
    } finally {
      TenantContextHolder.clear();
    }
  }

  private static String tierForHost(String slug, String host, String enterpriseHostBase) {
    if ("free".equals(slug)) {
      return "FREE";
    }
    if (HostTenantResolver.isEnterpriseHost(host, enterpriseHostBase)) {
      return "ENTERPRISE";
    }
    return "STANDARD";
  }
}
