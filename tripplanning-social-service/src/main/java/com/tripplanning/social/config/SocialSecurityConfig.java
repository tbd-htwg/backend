package com.tripplanning.social.config;

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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.tripplanning.common.auth.AuthProperties;
import com.tripplanning.common.auth.TestBearerImpersonationFilter;
import com.tripplanning.common.security.InternalApiAuthFilter;
import com.tripplanning.social.TenantContextFilter;

@Configuration
public class SocialSecurityConfig {

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
    public SecurityFilterChain socialFilterChain(
            HttpSecurity http,
            InternalApiAuthFilter internalApiAuthFilter,
            TenantContextFilter tenantContextFilter,
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
                                        .requestMatchers("/actuator/health", "/actuator/health/**")
                                        .permitAll()
                                        .requestMatchers("/internal/**")
                                        .permitAll()
                                        .requestMatchers(ant(HttpMethod.POST, "/api/v2/trips/*/like"))
                                        .authenticated()
                                        .requestMatchers(ant(HttpMethod.GET, "/api/v2/trips/search/countLikes"))
                                        .permitAll()
                                        .requestMatchers(ant(HttpMethod.HEAD, "/api/v2/users/*/likedTrips/*"))
                                        .permitAll()
                                        .requestMatchers(ant(HttpMethod.GET, "/api/v2/trips/*/community"))
                                        .permitAll()
                                        .requestMatchers(ant(HttpMethod.GET, "/api/v2/trips/*/comments"))
                                        .permitAll()
                                        .requestMatchers(ant(HttpMethod.GET, "/api/v2/comments"))
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .addFilterBefore(tenantContextFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(internalApiAuthFilter, UsernamePasswordAuthenticationFilter.class);

        TestBearerImpersonationFilter testBearerFilter = testBearerFilterProvider.getIfAvailable();
        if (testBearerFilter != null) {
            http.addFilterBefore(testBearerFilter, BearerTokenAuthenticationFilter.class);
        }

        return http.build();
    }

    private static AntPathRequestMatcher ant(HttpMethod method, String pattern) {
        return new AntPathRequestMatcher(pattern, method.name());
    }
}
