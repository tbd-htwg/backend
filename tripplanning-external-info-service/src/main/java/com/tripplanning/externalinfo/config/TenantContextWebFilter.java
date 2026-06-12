package com.tripplanning.externalinfo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.tripplanning.common.tenant.HostTenantResolver;
import com.tripplanning.common.tenant.TenantContext;
import com.tripplanning.common.tenant.TenantContextHolder;

import reactor.core.publisher.Mono;

@Component
@Order(20)
public class TenantContextWebFilter implements WebFilter {

  private final String hostBase;
  private final String enterpriseHostBase;

  public TenantContextWebFilter(
      @Value("${tripplanning.platform.host-base:k8s.tbd-htwg.de}") String hostBase,
      @Value("${tripplanning.platform.enterprise-host-base:enterprise.k8s.tbd-htwg.de}")
          String enterpriseHostBase) {
    this.hostBase = hostBase;
    this.enterpriseHostBase = enterpriseHostBase;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String host =
        HostTenantResolver.effectiveHost(
            exchange.getRequest().getHeaders().getFirst("X-Forwarded-Host"),
            exchange.getRequest().getHeaders().getFirst(HttpHeaders.HOST));
    String slug = HostTenantResolver.resolveSlug(host, hostBase, enterpriseHostBase);
    String tier = tierForHost(slug, host);
    TenantContextHolder.set(new TenantContext(slug, tier));
    return chain.filter(exchange).doFinally(signal -> TenantContextHolder.clear());
  }

  private String tierForHost(String slug, String host) {
    if ("free".equals(slug)) {
      return "FREE";
    }
    if (HostTenantResolver.isEnterpriseHost(host, enterpriseHostBase)) {
      return "ENTERPRISE";
    }
    return "STANDARD";
  }
}
