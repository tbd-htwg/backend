package com.tripplanning.platform.auth;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.tripplanning.common.auth.AppJwtDecoderConfiguration;
import com.tripplanning.common.auth.AuthProperties;

@Configuration
@Import(AppJwtDecoderConfiguration.class)
@EnableConfigurationProperties(AuthProperties.class)
public class JwtBeansConfig {

  @Bean
  JwtEncoder jwtEncoder(AuthProperties authProperties) {
    SecretKey key = hmacKey(authProperties.getJwtSecret());
    return new NimbusJwtEncoder(new ImmutableSecret<>(key));
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
