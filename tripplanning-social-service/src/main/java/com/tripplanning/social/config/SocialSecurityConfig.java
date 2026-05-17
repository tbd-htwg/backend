package com.tripplanning.social.config;

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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.tripplanning.common.security.InternalApiAuthFilter;

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
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin", "X-Internal-Secret"));
        config.setExposedHeaders(List.of("WWW-Authenticate"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain socialFilterChain(
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
                                        .requestMatchers(HttpMethod.POST, "/api/v2/trips/*/like")
                                        .authenticated()
                                        .requestMatchers(HttpMethod.GET, "/api/v2/trips/search/countLikes")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.HEAD, "/api/v2/users/*/likedTrips/*")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/api/v2/trips/*/community")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/api/v2/trips/*/comments")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/api/v2/comments")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .addFilterBefore(internalApiAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
