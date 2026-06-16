package com.tripplanning.platform.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.tripplanning.platform.infra.TenantDbCredentialProvider;
import com.tripplanning.platform.provisioning.ProvisioningCallbackOutcome;
import com.tripplanning.platform.provisioning.ProvisioningCallbackRequest;
import com.tripplanning.platform.provisioning.TenantProvisioningService;
import com.tripplanning.platform.tenant.TenantEntity;
import com.tripplanning.platform.tenant.TenantNaming;
import com.tripplanning.platform.tenant.TenantRepository;
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
      String objectPrefix) {}

  public record ProvisioningCallbackResponse(String outcome) {}

  private final TenantRepository tenantRepository;
  private final TenantDbCredentialProvider credentialProvider;
  private final TenantProvisioningService provisioningService;

  public InternalTenantController(
      TenantRepository tenantRepository,
      TenantDbCredentialProvider credentialProvider,
      TenantProvisioningService provisioningService) {
    this.tenantRepository = tenantRepository;
    this.credentialProvider = credentialProvider;
    this.provisioningService = provisioningService;
  public record ProvisioningCallbackRequest(String status, String message) {}

  private final TenantRepository tenantRepository;
  private final TenantProvisioningService tenantProvisioningService;

  public InternalTenantController(
      TenantRepository tenantRepository, TenantProvisioningService tenantProvisioningService) {
    this.tenantRepository = tenantRepository;
    this.tenantProvisioningService = tenantProvisioningService;
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
        TenantNaming.objectPrefix(entity.getSlug(), entity.getTier()));
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
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void provisioningCallback(
      @PathVariable String slug, @RequestBody(required = false) ProvisioningCallbackRequest request) {
    String status = request == null || request.status() == null ? "ACTIVE" : request.status();
    String message = request == null ? null : request.message();
    tenantProvisioningService.applyProvisioningCallback(slug, status, message);
  }
}
