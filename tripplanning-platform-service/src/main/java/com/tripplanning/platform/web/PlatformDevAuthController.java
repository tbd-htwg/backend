package com.tripplanning.platform.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.tripplanning.common.tenant.HostTenantResolver;
import com.tripplanning.platform.auth.PlatformAdminService;
import com.tripplanning.platform.auth.PlatformAppJwtService;
import com.tripplanning.platform.client.TripUserClient;
import com.tripplanning.platform.tenant.TenantDtos;

import jakarta.servlet.http.HttpServletRequest;

@Profile("local")
@RestController
@RequestMapping("/api/v2/auth")
public class PlatformDevAuthController {

  private final PlatformAdminService platformAdminService;
  private final PlatformAppJwtService platformAppJwtService;
  private final TripUserClient tripUserClient;
  private final String hostBase;

  public PlatformDevAuthController(
      PlatformAdminService platformAdminService,
      PlatformAppJwtService platformAppJwtService,
      TripUserClient tripUserClient,
      @Value("${tripplanning.platform.host-base:k8s.tbd-htwg.de}") String hostBase) {
    this.platformAdminService = platformAdminService;
    this.platformAppJwtService = platformAppJwtService;
    this.tripUserClient = tripUserClient;
    this.hostBase = hostBase;
  }

  @PostMapping("/dev-login")
  public TenantDtos.LoginResponse devLogin(
      @RequestBody TenantDtos.DevLoginRequest body, HttpServletRequest request) {
    if (body.email() == null || body.email().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required");
    }
    String email = body.email().trim();
    String forwardedHost =
        HostTenantResolver.effectiveHost(
            request.getHeader("X-Forwarded-Host"), request.getHeader("Host"));
    String tenantSlug = HostTenantResolver.resolveSlug(forwardedHost, hostBase);
    try {
      TenantDtos.UserResponseDto user =
          tripUserClient.provisionDev(forwardedHost, email, body.name());
      boolean platformAdmin = platformAdminService.isPlatformAdmin(email);
      String displayName =
          body.name() != null && !body.name().isBlank() ? body.name().trim() : user.name();
      String token =
          platformAppJwtService.createToken(
              user.id(), email, displayName, platformAdmin, tenantSlug);
      return new TenantDtos.LoginResponse("Bearer", token, user);
    } catch (IllegalStateException e) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), e);
    }
  }
}
