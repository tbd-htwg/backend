package com.tripplanning.social.config;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.tripplanning.common.auth.AuthProperties;

import java.nio.charset.StandardCharsets;

@Configuration
public class SocialJwtConfig {

    @Bean
    JwtDecoder jwtDecoder(AuthProperties authProperties) {
        byte[] secret = authProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException(
                    "tripplanning.auth.jwt-secret must be at least 32 bytes (set TRIPPLANNING_AUTH_JWT_SECRET)");
        }
        SecretKey key = new SecretKeySpec(secret, "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
