package com.tripplanning.api.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import com.tripplanning.common.security.InternalApiAuthFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.tripplanning.common.auth.AuthProperties;
import com.tripplanning.common.auth.TestBearerImpersonationFilter;
import com.tripplanning.user.UserRepository;

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
    config.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(
        List.of("Authorization", "Content-Type", "Accept", "Origin", TestBearerImpersonationFilter.ACT_AS_HEADER));
    config.setExposedHeaders(List.of("WWW-Authenticate"));
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);

    return source;
  }

  /**
   * Activated only when {@code tripplanning.auth.test-bearer-token} is set to a non-empty value.
   * The filter accepts the configured shared secret and the {@code X-Act-As-User} header to
   * authenticate seeders and load-test clients as any user. Production safety relies on the secret
   * being absent in production GitHub Environments.
   */
  @Bean
  @ConditionalOnExpression("'${tripplanning.auth.test-bearer-token:}'.length() > 0")
  public TestBearerImpersonationFilter testBearerImpersonationFilter(
      AuthProperties authProperties, UserRepository userRepository) {
    return new TestBearerImpersonationFilter(
        authProperties.getTestBearerToken(), id -> id == 0L || userRepository.existsById(id));
  }

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http,
      InternalApiAuthFilter internalApiAuthFilter,
      org.springframework.beans.factory.ObjectProvider<TestBearerImpersonationFilter>
          testBearerFilterProvider)
      throws Exception {
    http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors(Customizer.withDefaults())
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v2/auth/firebase")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v2/auth/google")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v2/auth/dev-login")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v2/auth/me")
                    .authenticated()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/internal/**")
                    .permitAll()
                    .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/search/**"))
                    .permitAll()
                    .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v2/users/{id:\\d+}"))
                    .permitAll()
                    .requestMatchers(
                        AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v2/users"),
                        AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v2/users/search"),
                        AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v2/users/search/**"))
                    .authenticated()
                    // Ant paths: MVC requestMatchers miss custom controllers under /api/v2/trips/* (feed, detail).
                    .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v2/**"))
                    .permitAll()
                    .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.HEAD, "/api/v2/**"))
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

    http.addFilterBefore(internalApiAuthFilter, UsernamePasswordAuthenticationFilter.class);

    TestBearerImpersonationFilter testBearerFilter = testBearerFilterProvider.getIfAvailable();
    if (testBearerFilter != null) {
      http.addFilterBefore(testBearerFilter, BearerTokenAuthenticationFilter.class);
    }

    return http.build();
  }
}
