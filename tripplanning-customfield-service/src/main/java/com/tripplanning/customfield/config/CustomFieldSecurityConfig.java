package com.tripplanning.customfield.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.tripplanning.common.auth.AuthProperties;
import com.tripplanning.common.auth.TestBearerImpersonationFilter;
import com.tripplanning.common.security.InternalApiAuthFilter;
import com.tripplanning.customfield.TenantContextFilter;

@Configuration
public class CustomFieldSecurityConfig {

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
        List.of(
            "Authorization",
            "Content-Type",
            "Accept",
            "Origin",
            "X-Internal-Secret",
            TestBearerImpersonationFilter.ACT_AS_HEADER));
    config.setExposedHeaders(List.of("WWW-Authenticate"));
    config.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  @Bean
  @ConditionalOnExpression("'${tripplanning.auth.test-bearer-token:}'.length() > 0")
  public TestBearerImpersonationFilter testBearerImpersonationFilter(AuthProperties authProperties) {
    return new TestBearerImpersonationFilter(authProperties.getTestBearerToken());
  }

  @Bean
  @Order(1)
  public SecurityFilterChain internalFilterChain(
      HttpSecurity http, InternalApiAuthFilter internalApiAuthFilter) throws Exception {
    http.securityMatcher(new AntPathRequestMatcher("/internal/**"))
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors(Customizer.withDefaults())
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .addFilterBefore(internalApiAuthFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  /**
   * Public reads (GET) and internal admin routes do not use OAuth2 resource-server filters.
   * PUT on trip custom fields validates JWT in {@link
   * com.tripplanning.customfield.TripCustomFieldController}.
   */
  @Bean
  @Order(2)
  public SecurityFilterChain customFieldFilterChain(
      HttpSecurity http,
      TenantContextFilter tenantContextFilter,
      org.springframework.beans.factory.ObjectProvider<TestBearerImpersonationFilter>
          testBearerFilterProvider)
      throws Exception {
    http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors(Customizer.withDefaults())
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

    TestBearerImpersonationFilter testBearerFilter = testBearerFilterProvider.getIfAvailable();
    if (testBearerFilter != null) {
      http.addFilterBefore(testBearerFilter, UsernamePasswordAuthenticationFilter.class);
    }
    http.addFilterAfter(tenantContextFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
