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

  public TenantContextFilter(
      @Value("${tripplanning.platform.host-base:k8s.tbd-htwg.de}") String hostBase) {
    this.hostBase = hostBase;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      String slug = HostTenantResolver.resolveSlug(request.getHeader("Host"), hostBase);
      TenantContextHolder.set(new TenantContext(slug, slug.equals("free") ? "FREE" : "STANDARD"));
      filterChain.doFilter(request, response);
    } finally {
      TenantContextHolder.clear();
    }
  }
}
