package com.tripplanning.externalinfo.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import com.tripplanning.common.auth.AuthProperties;
import com.tripplanning.common.auth.TestBearerImpersonationFilter;
import com.tripplanning.common.auth.TestBearerImpersonationWebFilter;

@Configuration
@EnableWebFluxSecurity
public class ExternalInfoSecurityConfig {

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
        config.setAllowedMethods(List.of("GET", "HEAD", "OPTIONS"));
        config.setAllowedHeaders(
                List.of(
                        "Authorization",
                        "Content-Type",
                        "Accept",
                        "Origin",
                        TestBearerImpersonationFilter.ACT_AS_HEADER));
        config.setExposedHeaders(List.of("WWW-Authenticate"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    @ConditionalOnExpression("'${tripplanning.auth.test-bearer-token:}'.length() > 0")
    public TestBearerImpersonationWebFilter testBearerImpersonationWebFilter(AuthProperties authProperties) {
        return new TestBearerImpersonationWebFilter(authProperties.getTestBearerToken());
    }

    @Bean
    public SecurityWebFilterChain externalInfoSecurity(
            ServerHttpSecurity http,
            org.springframework.beans.factory.ObjectProvider<TestBearerImpersonationWebFilter>
                    testBearerFilterProvider)
            throws Exception {
        TestBearerImpersonationWebFilter testBearerFilter = testBearerFilterProvider.getIfAvailable();
        if (testBearerFilter != null) {
            http.addFilterBefore(testBearerFilter, SecurityWebFiltersOrder.AUTHENTICATION);
        }
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults())
                .authorizeExchange(
                        exchanges ->
                                exchanges
                                        .pathMatchers(HttpMethod.OPTIONS, "/**")
                                        .permitAll()
                                        .pathMatchers("/actuator/health", "/actuator/health/**")
                                        .permitAll()
                                        .pathMatchers("/api/v1/**")
                                        .permitAll()
                                        .pathMatchers("/internal/**")
                                        .permitAll()
                                        .pathMatchers(HttpMethod.GET, "/api/v2/external/**")
                                        .permitAll()
                                        .anyExchange()
                                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }
}
