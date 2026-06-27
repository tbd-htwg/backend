package com.tripplanning.platform.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.tripplanning.common.tenant.HostTenantResolver;
import com.tripplanning.platform.auth.FirebaseCredentialVerifier;
import com.tripplanning.platform.infra.IdentityPlatformTenantIds;
import com.tripplanning.platform.auth.PlatformAdminService;
import com.tripplanning.platform.auth.PlatformAppJwtService;
import com.tripplanning.platform.client.TripUserClient;
import com.tripplanning.platform.tenant.TenantDtos;
import com.tripplanning.platform.tenant.TenantEntity;
import com.tripplanning.platform.tenant.TenantRepository;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v2/auth")
public class PlatformAuthController {

  private final FirebaseCredentialVerifier firebaseCredentialVerifier;
  private final PlatformAdminService platformAdminService;
  private final PlatformAppJwtService platformAppJwtService;
  private final TripUserClient tripUserClient;
  private final TenantRepository tenantRepository;
  private final String hostBase;
  private final String enterpriseHostBase;

  public PlatformAuthController(
      FirebaseCredentialVerifier firebaseCredentialVerifier,
      PlatformAdminService platformAdminService,
      PlatformAppJwtService platformAppJwtService,
      TripUserClient tripUserClient,
      TenantRepository tenantRepository,
      @Value("${tripplanning.platform.host-base:k8s.tbd-htwg.de}") String hostBase,
      @Value("${tripplanning.platform.enterprise-host-base:enterprise.k8s.tbd-htwg.de}")
          String enterpriseHostBase) {
    this.firebaseCredentialVerifier = firebaseCredentialVerifier;
    this.platformAdminService = platformAdminService;
    this.platformAppJwtService = platformAppJwtService;
    this.tripUserClient = tripUserClient;
    this.tenantRepository = tenantRepository;
    this.hostBase = hostBase;
    this.enterpriseHostBase = enterpriseHostBase;
  }

  @PostMapping("/firebase")
  public TenantDtos.LoginResponse firebase(
      @RequestBody TenantDtos.FirebaseLoginRequest body, HttpServletRequest request) {
    return exchangeFirebaseCredential(body.credential(), request);
  }

  @Deprecated
  @PostMapping("/google")
  public TenantDtos.LoginResponse google(
      @RequestBody TenantDtos.GoogleLoginRequest body, HttpServletRequest request) {
    return exchangeFirebaseCredential(body.credential(), request);
  }

  private TenantDtos.LoginResponse exchangeFirebaseCredential(
      String credential, HttpServletRequest request) {
    if (credential == null || credential.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "credential is required");
    }
    try {
      Jwt payload = firebaseCredentialVerifier.verify(credential);
      String email = payload.getClaimAsString("email");
      if (email == null || email.isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required in token");
      }
      String name = payload.getClaimAsString("name");
      String picture = payload.getClaimAsString("picture");
      String forwardedHost = forwardedHost(request);
      String tenantSlug = resolveTenantSlug(forwardedHost);
      validateIdentityTenant(payload, tenantSlug);
      TenantDtos.UserResponseDto user =
          tripUserClient.provisionIdentity(
              forwardedHost, payload.getSubject(), email, name, picture);
      boolean platformAdmin = platformAdminService.isPlatformAdmin(email);
      String token =
          platformAppJwtService.createToken(
              user.id(), email, name != null ? name : email, platformAdmin, tenantSlug);
      return new TenantDtos.LoginResponse("Bearer", token, user);
    } catch (IllegalStateException e) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), e);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
    } catch (JwtException e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid identity credential", e);
    }
  }

  @GetMapping("/me")
  public TenantDtos.UserResponseDto me(
      @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
    if (jwt == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    long id = Long.parseLong(jwt.getSubject());
    TenantDtos.UserResponseDto user = tripUserClient.getUser(forwardedHost(request), id);
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
    }
    return user;
  }

  private String forwardedHost(HttpServletRequest request) {
    return HostTenantResolver.effectiveHost(
        request.getHeader("X-Forwarded-Host"), request.getHeader("Host"));
  }

  private String resolveTenantSlug(String host) {
    return HostTenantResolver.resolveSlug(host, hostBase, enterpriseHostBase);
  }

  private void validateIdentityTenant(Jwt payload, String tenantSlug) {
    TenantEntity tenant =
        tenantRepository
            .findBySlug(tenantSlug)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantSlug));
    String expectedTenantId =
        IdentityPlatformTenantIds.effectiveTenantId(tenant.getIdentityPlatformTenantId());
    String tokenTenantId = firebaseCredentialVerifier.tenantIdFromToken(payload);

    if (expectedTenantId == null || expectedTenantId.isBlank()) {
      if (tokenTenantId != null && !tokenTenantId.isBlank()) {
        throw new IllegalArgumentException(
            "Tenant-scoped identity token cannot be used on the platform realm");
      }
      return;
    }

    if (!expectedTenantId.equals(tokenTenantId)) {
      throw new IllegalArgumentException(
          "Identity token tenant does not match the requested application tenant");
    }
  }
}
