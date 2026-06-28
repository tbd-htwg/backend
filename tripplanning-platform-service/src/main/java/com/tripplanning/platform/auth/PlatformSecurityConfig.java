package com.tripplanning.platform.auth;

import java.util.Arrays;
import java.util.List;

import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.tripplanning.common.security.InternalApiAuthFilter;
import com.tripplanning.platform.web.AdminCustomFieldController;

@Configuration
public class PlatformSecurityConfig {

  private static AntPathRequestMatcher ant(HttpMethod method, String pattern) {
    return AntPathRequestMatcher.antMatcher(method, pattern);
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource(
      @Value("${tripplanning.cors.allowed-origins}") String allowedOrigins) {
    CorsConfiguration config = new CorsConfiguration();
    List<String> origins =
        Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    List<String> exactOrigins = origins.stream().filter(origin -> !origin.contains("*")).toList();
    List<String> originPatterns = origins.stream().filter(origin -> origin.contains("*")).toList();
    config.setAllowedOrigins(exactOrigins);
    config.setAllowedOriginPatterns(originPatterns);
    config.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(
        List.of("Authorization", "Content-Type", "Accept", "Origin", "X-Internal-Secret", AdminCustomFieldController.ADMIN_TENANT_SLUG_HEADER));
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  @Bean
  @Order(1)
  public SecurityFilterChain internalFilterChain(
      HttpSecurity http, InternalApiAuthFilter internalApiAuthFilter) throws Exception {
    http.securityMatcher(new AntPathRequestMatcher("/internal/**"))
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors(Customizer.withDefaults())
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

    http.addFilterBefore(internalApiAuthFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  @Order(2)
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors(Customizer.withDefaults())
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(
            auth ->
                auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers(ant(HttpMethod.GET, "/api/v2/tenants/*/public-config"))
                    .permitAll()
                    .requestMatchers(ant(HttpMethod.GET, "/api/v2/tenants/*/users"))
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v2/auth/firebase")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v2/auth/google")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v2/auth/dev-login")
                    .permitAll()
                    .requestMatchers(
                        ant(
                            HttpMethod.PUT,
                            "/api/v2/admin/tenants/*/branding/icon/stub-upload/*"))
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

    return http.build();
  }
}
