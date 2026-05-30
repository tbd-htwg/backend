package com.tripplanning.common.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collections;
import java.util.function.LongPredicate;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * WebFlux counterpart to {@link TestBearerImpersonationFilter}. Strips the shared test bearer before
 * the OAuth2 resource-server filter so {@code permitAll} routes stay reachable under load tests.
 */
public class TestBearerImpersonationWebFilter implements WebFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String IMPERSONATION_TOKEN_VALUE = "test-bearer-impersonation";
    private static final String IMPERSONATION_ISSUER = "tripplanning-test-bearer";

    private final byte[] expectedTokenBytes;
    private final LongPredicate userExists;

    public TestBearerImpersonationWebFilter(String testBearerToken) {
        this(testBearerToken, id -> true);
    }

    public TestBearerImpersonationWebFilter(String testBearerToken, LongPredicate userExists) {
        if (testBearerToken == null || testBearerToken.isBlank()) {
            throw new IllegalStateException("testBearerToken must not be blank when filter is wired");
        }
        this.expectedTokenBytes = testBearerToken.getBytes(StandardCharsets.UTF_8);
        this.userExists = userExists;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null
                || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return chain.filter(exchange);
        }
        String presented = header.substring(BEARER_PREFIX.length()).trim();
        if (!constantTimeEquals(presented, expectedTokenBytes)) {
            return chain.filter(exchange);
        }

        long userId;
        String actAsRaw = exchange.getRequest().getHeaders().getFirst(TestBearerImpersonationFilter.ACT_AS_HEADER);
        if (actAsRaw == null || actAsRaw.isBlank()) {
            userId = 0L;
        } else {
            try {
                userId = Long.parseLong(actAsRaw.trim());
            } catch (NumberFormatException e) {
                return unauthorized(
                        exchange,
                        HttpStatus.BAD_REQUEST,
                        "invalid_request",
                        "X-Act-As-User must be a numeric user id");
            }
            if (userId != 0L && !userExists.test(userId)) {
                return unauthorized(
                        exchange,
                        HttpStatus.UNAUTHORIZED,
                        "invalid_token",
                        "X-Act-As-User refers to an unknown user");
            }
        }

        Instant now = Instant.now();
        Jwt jwt =
                Jwt.withTokenValue(IMPERSONATION_TOKEN_VALUE)
                        .header("alg", "none")
                        .subject(String.valueOf(userId))
                        .issuer(IMPERSONATION_ISSUER)
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(60))
                        .claim("act_as_user_id", userId)
                        .build();
        JwtAuthenticationToken auth =
                new JwtAuthenticationToken(jwt, Collections.emptyList(), String.valueOf(userId));
        auth.setAuthenticated(true);

        ServerWebExchange mutated =
                exchange.mutate().request(stripAuthorizationHeader(exchange.getRequest())).build();
        return chain.filter(mutated)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
    }

    private static Mono<Void> unauthorized(
            ServerWebExchange exchange, HttpStatus status, String oauthError, String description) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse()
                .getHeaders()
                .add(
                        HttpHeaders.WWW_AUTHENTICATE,
                        "Bearer error=\"" + oauthError + "\", error_description=\"" + description + "\"");
        return exchange.getResponse().setComplete();
    }

    private static boolean constantTimeEquals(String presented, byte[] expectedBytes) {
        byte[] presentedBytes = presented.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(presentedBytes, expectedBytes);
    }

    private static ServerHttpRequest stripAuthorizationHeader(ServerHttpRequest request) {
        return new ServerHttpRequestDecorator(request) {
            @Override
            public HttpHeaders getHeaders() {
                HttpHeaders headers = new HttpHeaders();
                headers.putAll(super.getHeaders());
                headers.remove(HttpHeaders.AUTHORIZATION);
                return headers;
            }
        };
    }
}
