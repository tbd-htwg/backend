package com.tripplanning.platform.provisioning;

import java.time.Instant;
import java.util.List;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripplanning.platform.infra.IdentityPlatformClient;
import com.tripplanning.platform.infra.TenantDispatchPayload;
import com.tripplanning.platform.infra.TenantInfrastructureProvisioner;
import com.tripplanning.platform.tenant.ProvisioningJson;
import com.tripplanning.platform.tenant.TenantEntity;
import com.tripplanning.platform.tenant.TenantNaming;
import com.tripplanning.platform.tenant.TenantRepository;
import com.tripplanning.platform.tenant.TenantStatus;
import com.tripplanning.platform.tenant.TenantTier;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantProvisioningService {

  private final TenantRepository tenantRepository;
  private final ProvisioningJson provisioningJson;
  private final IdentityPlatformClient identityPlatformClient;
  private final TenantInfrastructureProvisioner infrastructureProvisioner;

  @Async
  public void provisionAsync(String tenantId) {
    tenantRepository
        .findById(tenantId)
        .ifPresent(
            tenant -> {
              try {
                runProvisioning(tenant);
              } catch (Exception e) {
                log.error("Provisioning failed for tenant {}", tenantId, e);
                failTenant(tenantId, e.getMessage());
              }
            });
  }

  @Transactional
  public void retry(String tenantId) {
    TenantEntity tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
    if (tenant.getStatus() != TenantStatus.FAILED) {
      throw new IllegalStateException("Only failed tenants can be retried");
    }
    tenant.setStatus(TenantStatus.PROVISIONING);
    tenant.setProvisioningError(null);
    tenant.setProvisioningStepsJson(
        provisioningJson.writeSteps(ProvisioningStepDefinitions.initialSteps(tenant.getTier())));
    tenant.setUpdatedAt(Instant.now());
    tenantRepository.save(tenant);
    provisionAsync(tenantId);
  }

  private void runProvisioning(TenantEntity tenant) {
    TenantTier tier = tenant.getTier();

    updateStep(tenant.getId(), tier, 0, null);
    var idp =
        identityPlatformClient.createTenant(tenant.getSlug(), tenant.getDisplayName());
    tenant = reload(tenant.getId());
    tenant.setIdentityPlatformTenantId(idp.identityPlatformTenantId());
    tenant.setEnabledAuthProvidersJson(provisioningJson.writeProviders(idp.enabledProviders()));
    tenantRepository.save(tenant);

    updateStep(tenant.getId(), tier, 1, null);
    TenantDispatchPayload payload = toDispatchPayload(reload(tenant.getId()));

    if (tier == TenantTier.STANDARD) {
      infrastructureProvisioner.triggerStandardTenant(payload);
      updateStep(tenant.getId(), tier, 2, null);
      updateStep(tenant.getId(), tier, 3, null);
      updateStep(tenant.getId(), tier, 4, null);
      complete(tenant.getId(), tier);
      return;
    }

    if (tier == TenantTier.ENTERPRISE) {
      infrastructureProvisioner.triggerEnterpriseTenant(payload);
      updateStep(tenant.getId(), tier, 2, null);
      updateStep(tenant.getId(), tier, 3, null);
      updateStep(tenant.getId(), tier, 4, null);
      updateStep(tenant.getId(), tier, 5, null);

      tenant = reload(tenant.getId());
      if (tenant.getFirestoreDatabase() == null || tenant.getFirestoreDatabase().isBlank()) {
        tenant.setFirestoreDatabase("(default)-" + tenant.getSlug());
      }
      if (tenant.getGcsBucket() == null || tenant.getGcsBucket().isBlank()) {
        tenant.setGcsBucket(TenantNaming.gcsBucket(tenant.getSlug(), TenantTier.ENTERPRISE));
      }
      tenantRepository.save(tenant);

      updateStep(tenant.getId(), tier, 6, null);
      complete(tenant.getId(), tier);
    }
  }

  private TenantDispatchPayload toDispatchPayload(TenantEntity tenant) {
    return new TenantDispatchPayload(
        tenant.getSlug(),
        tenant.getDisplayName(),
        tenant.getTier(),
        tenant.getHostUrl(),
        tenant.getNamespace(),
        tenant.getDbName(),
        tenant.getSearchIndex(),
        tenant.getFrontendPath(),
        tenant.getIdentityPlatformTenantId(),
        tenant.getImageTag(),
        tenant.getGcsBucket());
  }

  private void updateStep(String tenantId, TenantTier tier, int doneThrough, String failedKey) {
    TenantEntity tenant = reload(tenantId);
    List<com.tripplanning.platform.tenant.TenantDtos.ProvisioningStepDto> steps =
        tier == TenantTier.ENTERPRISE
            ? ProvisioningStepDefinitions.enterpriseSteps(doneThrough, failedKey)
            : ProvisioningStepDefinitions.standardSteps(doneThrough, failedKey);
    tenant.setProvisioningStepsJson(provisioningJson.writeSteps(steps));
    tenant.setUpdatedAt(Instant.now());
    tenantRepository.save(tenant);
  }

  private void complete(String tenantId, TenantTier tier) {
    TenantEntity tenant = reload(tenantId);
    tenant.setStatus(TenantStatus.ACTIVE);
    tenant.setProvisioningStepsJson(
        provisioningJson.writeSteps(ProvisioningStepDefinitions.completed(tier)));
    tenant.setUpdatedAt(Instant.now());
    tenantRepository.save(tenant);
  }

  private void failTenant(String tenantId, String message) {
    tenantRepository
        .findById(tenantId)
        .ifPresent(
            tenant -> {
              tenant.setStatus(TenantStatus.FAILED);
              tenant.setProvisioningError(message);
              tenant.setUpdatedAt(Instant.now());
              tenantRepository.save(tenant);
            });
  }

  private TenantEntity reload(String tenantId) {
    return tenantRepository
        .findById(tenantId)
        .orElseThrow(() -> new IllegalStateException("Tenant vanished: " + tenantId));
  }
}
