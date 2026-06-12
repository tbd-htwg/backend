package com.tripplanning.platform.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.tripplanning.platform.tenant.TenantEntity;
import com.tripplanning.platform.tenant.TenantNaming;
import com.tripplanning.platform.tenant.TenantRepository;
import com.tripplanning.platform.tenant.TenantStatus;

@RestController
@RequestMapping("/internal/tenants")
public class InternalTenantController {

  public record TenantRuntimeDto(
      String slug,
      String tier,
      String status,
      String dbName,
      String searchIndex,
      String gcsBucket,
      String objectPrefix) {}

  private final TenantRepository tenantRepository;

  public InternalTenantController(TenantRepository tenantRepository) {
    this.tenantRepository = tenantRepository;
  }

  @GetMapping("/{slug}")
  public TenantRuntimeDto runtime(@PathVariable String slug) {
    TenantEntity entity =
        tenantRepository
            .findBySlug(slug.toLowerCase())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    if (entity.getStatus() != TenantStatus.ACTIVE && entity.getStatus() != TenantStatus.PROVISIONING) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not active");
    }
    return new TenantRuntimeDto(
        entity.getSlug(),
        entity.getTier().name(),
        entity.getStatus().name(),
        entity.getDbName(),
        entity.getSearchIndex(),
        entity.getGcsBucket(),
        TenantNaming.objectPrefix(entity.getSlug(), entity.getTier()));
  }
}
