package com.tripplanning.platform.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.tripplanning.platform.infra.TenantDbCredentialProvider;
import com.tripplanning.platform.provisioning.ProvisioningCallbackOutcome;
import com.tripplanning.platform.provisioning.ProvisioningCallbackRequest;
import com.tripplanning.platform.provisioning.TenantProvisioningService;
import com.tripplanning.platform.tenant.TenantEntity;
import com.tripplanning.platform.tenant.TenantNaming;
import com.tripplanning.platform.tenant.TenantRepository;
import com.tripplanning.platform.tenant.TenantService;
import com.tripplanning.platform.tenant.TenantStatus;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/internal/tenants")
public class InternalTenantController {

  public record TenantRuntimeDto(
      String slug,
      String tier,
      String status,
      String dbName,
      String dbUser,
      String dbPassword,
      String searchIndex,
      String gcsBucket,
      String objectPrefix,
      boolean publicTripAccess,
      boolean publicImageAccess) {}

  public record ProvisioningCallbackResponse(String outcome) {}
  public record TenantDeletionResponse(String outcome) {}

  private final TenantRepository tenantRepository;
  private final TenantDbCredentialProvider credentialProvider;
  private final TenantProvisioningService provisioningService;
  private final TenantService tenantService;

  public InternalTenantController(
      TenantRepository tenantRepository,
      TenantDbCredentialProvider credentialProvider,
      TenantProvisioningService provisioningService,
      TenantService tenantService) {
    this.tenantRepository = tenantRepository;
    this.credentialProvider = credentialProvider;
    this.provisioningService = provisioningService;
    this.tenantService = tenantService;
  }

  @GetMapping("/{slug}")
  public TenantRuntimeDto runtime(@PathVariable String slug) {
    TenantEntity entity = loadRuntimeTenant(slug);
    TenantDbCredentialProvider.DbCredentials credentials = credentialProvider.resolve(entity);
    return new TenantRuntimeDto(
        entity.getSlug(),
        entity.getTier().name(),
        entity.getStatus().name(),
        entity.getDbName(),
        credentials.userName(),
        credentials.password(),
        entity.getSearchIndex(),
        entity.getGcsBucket(),
        TenantNaming.objectPrefix(entity.getSlug(), entity.getTier()),
        entity.isPublicTripAccess(),
        entity.isPublicImageAccess());
  }

  @PostMapping("/{slug}/provisioning-callback")
  public ProvisioningCallbackResponse provisioningCallback(
      @PathVariable String slug, @Valid @RequestBody ProvisioningCallbackRequest request) {
    try {
      ProvisioningCallbackOutcome outcome =
          provisioningService.completeProvisioningFromCallback(slug, request);
      return new ProvisioningCallbackResponse(outcome.name());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalStateException e) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
    }
  }

  @DeleteMapping("/{slug}")
  public TenantDeletionResponse delete(@PathVariable String slug) {
    try {
      boolean deleted = tenantService.deleteBySlug(slug);
      return new TenantDeletionResponse(deleted ? "DELETED" : "ALREADY_ABSENT");
    } catch (IllegalStateException e) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
    }
  }

  private TenantEntity loadRuntimeTenant(String slug) {
    TenantEntity entity =
        tenantRepository
            .findBySlug(slug.toLowerCase())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    if (entity.getStatus() != TenantStatus.ACTIVE
        && entity.getStatus() != TenantStatus.PROVISIONING) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not active");
    }
    return entity;
  }
}
