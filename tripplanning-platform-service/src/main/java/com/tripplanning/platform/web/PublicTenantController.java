package com.tripplanning.platform.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.tripplanning.platform.client.TripUserClient;
import com.tripplanning.platform.tenant.TenantDtos;
import com.tripplanning.platform.tenant.TenantService;

@RestController
@RequestMapping("/api/v2/tenants")
public class PublicTenantController {

  private final TenantService tenantService;
  private final TripUserClient tripUserClient;

  public PublicTenantController(TenantService tenantService, TripUserClient tripUserClient) {
    this.tenantService = tenantService;
    this.tripUserClient = tripUserClient;
  }

  @GetMapping("/{slug}/public-config")
  public TenantDtos.PublicTenantConfigDto publicConfig(@PathVariable String slug) {
    try {
      return tenantService.getPublicConfig(slug.toLowerCase());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  @GetMapping("/{slug}/users")
  public List<TenantDtos.TenantUserDto> publicUsers(@PathVariable String slug) {
    TenantDtos.PublicTenantConfigDto config = tenantService.getPublicConfig(slug.toLowerCase());
    if (!"ACTIVE".equalsIgnoreCase(config.status())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not active");
    }
    String host = TripUserClient.hostHeaderFromUrl(config.hostUrl());
    try {
      List<TenantDtos.TenantUserDto> users = tripUserClient.listUsers(host);
      return users != null ? users : List.of();
    } catch (Exception e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Failed to list tenant users: " + e.getMessage());
    }
  }
}
