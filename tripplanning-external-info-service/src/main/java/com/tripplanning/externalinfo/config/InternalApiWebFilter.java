package com.tripplanning.externalinfo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * When {@code tripplanning.services.internal-secret} is set, requires matching
 * {@code X-Internal-Secret} on {@code /internal/**} routes.
 */
@Component
@Order(-100)
public class InternalApiWebFilter implements WebFilter {

    private final String internalSecret;

    public InternalApiWebFilter(
            @Value("${tripplanning.services.internal-secret:}") String internalSecret) {
        this.internalSecret = internalSecret;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path == null || !path.startsWith("/internal/")) {
            return chain.filter(exchange);
        }
        if (internalSecret == null || internalSecret.isBlank()) {
            return chain.filter(exchange);
        }
        String provided = exchange.getRequest().getHeaders().getFirst("X-Internal-Secret");
        if (internalSecret.equals(provided)) {
            return chain.filter(exchange);
        }
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        return exchange.getResponse().setComplete();
    }
}
