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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.tripplanning.auth.AuthProperties;
import com.tripplanning.auth.TestBearerImpersonationFilter;
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
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin"));
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
    return new TestBearerImpersonationFilter(authProperties.getTestBearerToken(), userRepository);
  }

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http,
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
                    .requestMatchers(HttpMethod.POST, "/api/v2/auth/google")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v2/auth/dev-login")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v2/auth/me")
                    .authenticated()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/search/**")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET, "/api/v2/trips/*/liked-by-current-user")
                    .authenticated()
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

    TestBearerImpersonationFilter testBearerFilter = testBearerFilterProvider.getIfAvailable();
    if (testBearerFilter != null) {
      http.addFilterBefore(testBearerFilter, BearerTokenAuthenticationFilter.class);
    }

    return http.build();
  }
}
