package com.tripplanning.common.auth;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import java.nio.charset.StandardCharsets;

/** Shared HS256 decoder for app JWTs issued by trip-service (or a future auth-service). */
@Configuration
public class AppJwtDecoderConfiguration {

    @Bean
    @ConditionalOnMissingBean
    JwtDecoder jwtDecoder(AuthProperties authProperties) {
        return NimbusJwtDecoder.withSecretKey(hmacKey(authProperties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    @ConditionalOnWebApplication(type = Type.REACTIVE)
    @ConditionalOnMissingBean
    ReactiveJwtDecoder reactiveJwtDecoder(AuthProperties authProperties) {
        return NimbusReactiveJwtDecoder.withSecretKey(hmacKey(authProperties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    private static SecretKey hmacKey(AuthProperties authProperties) {
        byte[] secret = authProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException(
                    "tripplanning.auth.jwt-secret must be at least 32 bytes (set TRIPPLANNING_AUTH_JWT_SECRET)");
        }
        return new SecretKeySpec(secret, "HmacSHA256");
    }
}
