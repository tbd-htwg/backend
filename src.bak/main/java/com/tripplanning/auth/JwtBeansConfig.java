package com.tripplanning.auth;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.nio.charset.StandardCharsets;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class JwtBeansConfig {

  @Bean
  JwtEncoder jwtEncoder(AuthProperties authProperties) {
    SecretKey key = hmacKey(authProperties.getJwtSecret());
    return new NimbusJwtEncoder(new ImmutableSecret<>(key));
  }

  @Bean
  JwtDecoder jwtDecoder(AuthProperties authProperties) {
    SecretKey key = hmacKey(authProperties.getJwtSecret());
    return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
  }

  private static SecretKey hmacKey(String raw) {
    byte[] secret = raw.getBytes(StandardCharsets.UTF_8);
    if (secret.length < 32) {
      throw new IllegalStateException(
          "tripplanning.auth.jwt-secret must be at least 32 bytes (set TRIPPLANNING_AUTH_JWT_SECRET)");
    }
    return new SecretKeySpec(secret, "HmacSHA256");
  }
}
