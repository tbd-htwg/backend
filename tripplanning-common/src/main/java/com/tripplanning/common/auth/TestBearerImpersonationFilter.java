package com.tripplanning.common.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Optional impersonation filter for seeders and load-test clients. When the configured shared test
 * bearer token is presented as {@code Authorization: Bearer <token>} together with optional
 * {@code X-Act-As-User: <userId>}, the request is authenticated as that user (no JWT verification).
 */
public class TestBearerImpersonationFilter extends OncePerRequestFilter {

    public static final String ACT_AS_HEADER = "X-Act-As-User";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String IMPERSONATION_TOKEN_VALUE = "test-bearer-impersonation";
    private static final String IMPERSONATION_ISSUER = "tripplanning-test-bearer";

    private final byte[] expectedTokenBytes;
    private final LongPredicate userExists;
    private final LongFunction<String> appJwtFactory;

    public TestBearerImpersonationFilter(String testBearerToken) {
        this(testBearerToken, id -> true);
    }

    public TestBearerImpersonationFilter(String testBearerToken, LongPredicate userExists) {
        this(testBearerToken, userExists, null);
    }

    /**
     * When {@code appJwtFactory} is set, a matching test bearer is exchanged for a real application
     * JWT on the request (for OAuth2 resource-server + Spring Data REST). Otherwise the filter sets
     * an impersonation {@link JwtAuthenticationToken} directly and strips {@code Authorization}.
     */
    public TestBearerImpersonationFilter(
            String testBearerToken, LongPredicate userExists, LongFunction<String> appJwtFactory) {
        if (testBearerToken == null || testBearerToken.isBlank()) {
            throw new IllegalStateException("testBearerToken must not be blank when filter is wired");
        }
        this.expectedTokenBytes = testBearerToken.getBytes(StandardCharsets.UTF_8);
        this.userExists = userExists;
        this.appJwtFactory = appJwtFactory;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null
                || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            chain.doFilter(request, response);
            return;
        }
        String presented = header.substring(BEARER_PREFIX.length()).trim();
        if (!constantTimeEquals(presented, expectedTokenBytes)) {
            chain.doFilter(request, response);
            return;
        }

        long userId;
        String actAsRaw = request.getHeader(ACT_AS_HEADER);
        if (actAsRaw == null || actAsRaw.isBlank()) {
            userId = 0L;
        } else {
            try {
                userId = Long.parseLong(actAsRaw.trim());
            } catch (NumberFormatException e) {
                sendUnauthorized(
                        response,
                        HttpServletResponse.SC_BAD_REQUEST,
                        "invalid_request",
                        "X-Act-As-User must be a numeric user id");
                return;
            }
            if (userId != 0L && !userExists.test(userId)) {
                sendUnauthorized(
                        response,
                        HttpServletResponse.SC_UNAUTHORIZED,
                        "invalid_token",
                        "X-Act-As-User refers to an unknown user");
                return;
            }
        }

        if (appJwtFactory != null) {
            String appJwt = appJwtFactory.apply(userId);
            if (appJwt == null || appJwt.isBlank()) {
                sendUnauthorized(
                        response,
                        HttpServletResponse.SC_UNAUTHORIZED,
                        "invalid_token",
                        "Could not issue application JWT for test bearer impersonation");
                return;
            }
            SecurityContextHolder.clearContext();
            chain.doFilter(replaceAuthorizationHeader(request, appJwt), response);
            return;
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
        SecurityContextHolder.getContext().setAuthentication(auth);

        chain.doFilter(stripAuthorizationHeader(request), response);
    }

    private static boolean constantTimeEquals(String presented, byte[] expectedBytes) {
        byte[] presentedBytes = presented.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(presentedBytes, expectedBytes);
    }

    private static void sendUnauthorized(
            HttpServletResponse response, int status, String oauthError, String description)
            throws IOException {
        response.setHeader(
                HttpHeaders.WWW_AUTHENTICATE,
                "Bearer error=\"" + oauthError + "\", error_description=\"" + description + "\"");
        response.sendError(status, description);
    }

    private static HttpServletRequestWrapper replaceAuthorizationHeader(
            HttpServletRequest request, String jwt) {
        String bearer = BEARER_PREFIX + jwt;
        return new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) {
                    return bearer;
                }
                return super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) {
                    return Collections.enumeration(List.of(bearer));
                }
                return super.getHeaders(name);
            }
        };
    }

    private static HttpServletRequestWrapper stripAuthorizationHeader(HttpServletRequest request) {
        return new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) {
                    return null;
                }
                return super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) {
                    return Collections.emptyEnumeration();
                }
                return super.getHeaders(name);
            }

            @Override
            public Enumeration<String> getHeaderNames() {
                List<String> filtered =
                        Collections.list(super.getHeaderNames()).stream()
                                .filter(n -> !HttpHeaders.AUTHORIZATION.equalsIgnoreCase(n))
                                .toList();
                return Collections.enumeration(filtered);
            }
        };
    }
}
