package com.tripplanning.platform.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.tripplanning.platform.tenant.TenantDtos;
import com.tripplanning.platform.tenant.TenantService;

@RestController
@RequestMapping("/api/v2/tenants")
public class PublicTenantController {

  private final TenantService tenantService;

  public PublicTenantController(TenantService tenantService) {
    this.tenantService = tenantService;
  }

  @GetMapping("/{slug}/public-config")
  public TenantDtos.PublicTenantConfigDto publicConfig(@PathVariable String slug) {
    try {
      return tenantService.getPublicConfig(slug.toLowerCase());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }
}
