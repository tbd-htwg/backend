package com.tripplanning.platform.auth;

import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.tripplanning.common.auth.AuthProperties;

@Service
public class PlatformAppJwtService {

  private static final String ISSUER = "tripplanning";

  private final JwtEncoder jwtEncoder;
  private final AuthProperties authProperties;

  public PlatformAppJwtService(JwtEncoder jwtEncoder, AuthProperties authProperties) {
    this.jwtEncoder = jwtEncoder;
    this.authProperties = authProperties;
  }

  public String createToken(
      long userId, String email, String name, boolean platformAdmin, String tenantSlug) {
    Instant now = Instant.now();
    Instant exp = now.plusSeconds(authProperties.getJwtExpirationSeconds());
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(String.valueOf(userId))
            .issuedAt(now)
            .expiresAt(exp)
            .claim("email", email != null ? email : "")
            .claim("name", name != null ? name : "")
            .claim("platform_admin", platformAdmin)
            .claim("tenant_slug", tenantSlug != null ? tenantSlug : "")
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }
}
