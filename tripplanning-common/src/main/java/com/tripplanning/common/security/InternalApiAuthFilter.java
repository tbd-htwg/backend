package com.tripplanning.common.security;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * When {@code tripplanning.services.internal-secret} is set, requires matching
 * {@code X-Internal-Secret} on {@code /internal/**} routes.
 */
@Component
@RequiredArgsConstructor
public class InternalApiAuthFilter extends OncePerRequestFilter {

    @Value("${tripplanning.services.internal-secret:}")
    private String internalSecret;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (internalSecret == null || internalSecret.isBlank()) {
            return true;
        }
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String provided = request.getHeader("X-Internal-Secret");
        if (!internalSecret.equals(provided)) {
            response.sendError(HttpStatus.FORBIDDEN.value(), "Forbidden");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
