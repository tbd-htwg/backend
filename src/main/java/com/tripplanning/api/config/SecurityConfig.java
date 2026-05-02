package com.tripplanning.api.config;

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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

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
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin"));
    config.setExposedHeaders(List.of("WWW-Authenticate"));
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);

    return source;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors(Customizer.withDefaults())
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v2/auth/google")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v2/auth/dev-login")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v2/auth/me")
                    .authenticated()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    // Full-text search and facet suggests (public; paths under Spring MVC /api/search).
                    .requestMatchers("/api/search/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v2/users/{id:\\d+}")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v2/users",
                        "/api/v2/users/search",
                        "/api/v2/users/search/**")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v2/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.HEAD, "/api/v2/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

    return http.build();
  }
}
