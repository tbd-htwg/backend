package com.tripplanning.platform.auth;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.tripplanning.common.security.InternalApiAuthFilter;

@Configuration
public class PlatformSecurityConfig {

  @Bean
  public CorsConfigurationSource corsConfigurationSource(
      @Value("${tripplanning.cors.allowed-origins}") String allowedOrigins) {
    CorsConfiguration config = new CorsConfiguration();
    List<String> origins =
        Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    config.setAllowedOrigins(origins);
    config.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin"));
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http, InternalApiAuthFilter internalApiAuthFilter) throws Exception {
    http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors(Customizer.withDefaults())
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/internal/**")
                    .permitAll()
                    .requestMatchers(
                        RegexRequestMatcher.regexMatcher(
                            HttpMethod.GET, "^/api/v2/tenants/[^/]+/public-config$"))
                    .permitAll()
                    .requestMatchers(
                        RegexRequestMatcher.regexMatcher(
                            HttpMethod.GET, "^/api/v2/tenants/[^/]+/users$"))
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v2/auth/firebase")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v2/auth/google")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v2/auth/dev-login")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v2/auth/me")
                    .authenticated()
                    .requestMatchers("/api/v2/admin/**")
                    .hasAuthority("SCOPE_platform_admin")
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.jwt(
                    jwt ->
                        jwt.jwtAuthenticationConverter(
                            token -> {
                              boolean admin =
                                  Boolean.TRUE.equals(token.getClaim("platform_admin"));
                              var authorities =
                                  admin
                                      ? List.of(
                                          new org.springframework.security.core.authority
                                              .SimpleGrantedAuthority("SCOPE_platform_admin"))
                                      : List.<org.springframework.security.core.GrantedAuthority>
                                          of();
                              return new org.springframework.security.oauth2.server.resource
                                  .authentication.JwtAuthenticationToken(token, authorities);
                            })));

    http.addFilterBefore(internalApiAuthFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
