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

  public TenantContextWebFilter(
      @Value("${tripplanning.platform.host-base:k8s.tbd-htwg.de}") String hostBase) {
    this.hostBase = hostBase;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String slug =
        HostTenantResolver.resolveSlug(
            exchange.getRequest().getHeaders().getFirst(HttpHeaders.HOST), hostBase);
    TenantContextHolder.set(new TenantContext(slug, slug.equals("free") ? "FREE" : "STANDARD"));
    return chain.filter(exchange).doFinally(signal -> TenantContextHolder.clear());
  }
}
