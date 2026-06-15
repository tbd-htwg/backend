package com.tripplanning.platform.provisioning;

import java.time.Instant;
import java.util.List;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripplanning.platform.config.PlatformProperties;
import com.tripplanning.platform.infra.IdentityPlatformClient;
import com.tripplanning.platform.infra.StandardDataProvisioner;
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
  private final StandardDataProvisioner standardDataProvisioner;
  private final PlatformProperties platformProperties;

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
  public ProvisioningCallbackOutcome completeProvisioningFromCallback(
      String slug, ProvisioningCallbackRequest request) {
    TenantEntity tenant =
        tenantRepository
            .findBySlug(slug.toLowerCase())
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + slug));

    if (tenant.getStatus() == TenantStatus.ACTIVE) {
      return ProvisioningCallbackOutcome.ALREADY_ACTIVE;
    }
    if (tenant.getStatus() != TenantStatus.PROVISIONING) {
      throw new IllegalStateException(
          "Tenant " + slug + " is not provisioning (status=" + tenant.getStatus() + ")");
    }

    if ("FAILED".equalsIgnoreCase(request.status())) {
      String failedStep =
          request.failedStep() != null && !request.failedStep().isBlank()
              ? request.failedStep()
              : "terraform_infra";
      updateStep(tenant.getId(), tenant.getTier(), stepIndexForKey(tenant.getTier(), failedStep), failedStep, false);
      failTenant(
          tenant.getId(),
          request.message() != null && !request.message().isBlank()
              ? request.message()
              : "Infrastructure provisioning failed");
      return ProvisioningCallbackOutcome.COMPLETED;
    }

    if (!"SUCCESS".equalsIgnoreCase(request.status())) {
      throw new IllegalArgumentException("status must be SUCCESS or FAILED");
    }

    applyResourceOverrides(tenant, request);
    tenantRepository.save(tenant);

    TenantTier tier = tenant.getTier();
    boolean stubLabels = false;

    if (tier == TenantTier.STANDARD) {
      updateStep(tenant.getId(), tier, 3, null, stubLabels);
      standardDataProvisioner.createSearchIndex(reload(tenant.getId()).getSearchIndex());
      updateStep(tenant.getId(), tier, 4, null, stubLabels);
      complete(tenant.getId(), tier, stubLabels);
      return ProvisioningCallbackOutcome.COMPLETED;
    }

    if (tier == TenantTier.ENTERPRISE) {
      updateStep(tenant.getId(), tier, 3, null, stubLabels);
      updateStep(tenant.getId(), tier, 4, null, stubLabels);
      updateStep(tenant.getId(), tier, 5, null, stubLabels);

      tenant = reload(tenant.getId());
      if (tenant.getFirestoreDatabase() == null || tenant.getFirestoreDatabase().isBlank()) {
        tenant.setFirestoreDatabase("(default)-" + tenant.getSlug());
      }
      if (tenant.getGcsBucket() == null || tenant.getGcsBucket().isBlank()) {
        tenant.setGcsBucket(TenantNaming.gcsBucket(tenant.getSlug(), TenantTier.ENTERPRISE));
      }
      tenantRepository.save(tenant);

      updateStep(tenant.getId(), tier, 6, null, stubLabels);
      complete(tenant.getId(), tier, stubLabels);
      return ProvisioningCallbackOutcome.COMPLETED;
    }

    throw new IllegalStateException("Callback not supported for tier " + tier);
  }

  private void applyResourceOverrides(TenantEntity tenant, ProvisioningCallbackRequest request) {
    if (request.dbName() != null && !request.dbName().isBlank()) {
      tenant.setDbName(request.dbName());
    }
    if (request.dbUser() != null && !request.dbUser().isBlank()) {
      tenant.setDbUser(request.dbUser());
    }
    if (request.gcsBucket() != null && !request.gcsBucket().isBlank()) {
      tenant.setGcsBucket(request.gcsBucket());
    }
    if (request.firestoreDatabase() != null && !request.firestoreDatabase().isBlank()) {
      tenant.setFirestoreDatabase(request.firestoreDatabase());
    }
    if (request.identityPlatformTenantId() != null
        && !request.identityPlatformTenantId().isBlank()) {
      tenant.setIdentityPlatformTenantId(request.identityPlatformTenantId());
    }
    tenant.setUpdatedAt(Instant.now());
  }

  private static int stepIndexForKey(TenantTier tier, String failedKey) {
    var steps =
        tier == TenantTier.ENTERPRISE
            ? ProvisioningStepDefinitions.enterpriseSteps(0, failedKey)
            : ProvisioningStepDefinitions.standardSteps(0, failedKey);
    for (int i = 0; i < steps.size(); i++) {
      if (failedKey.equals(steps.get(i).key())) {
        return i;
      }
    }
    return tier == TenantTier.ENTERPRISE ? 2 : 2;
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
        provisioningJson.writeSteps(
            ProvisioningStepDefinitions.initialSteps(tenant.getTier(), useStubLabels())));
    tenant.setUpdatedAt(Instant.now());
    tenantRepository.save(tenant);
    provisionAsync(tenantId);
  }

  @Transactional
  public void applyProvisioningCallback(String slug, String status, String message) {
    TenantEntity tenant =
        tenantRepository
            .findBySlug(slug.toLowerCase())
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + slug));

    if ("FAILED".equalsIgnoreCase(status)) {
      failTenant(
          tenant.getId(),
          message == null || message.isBlank() ? "Infrastructure provisioning failed" : message);
      return;
    }

    if (!"ACTIVE".equalsIgnoreCase(status)) {
      throw new IllegalArgumentException("Unsupported provisioning callback status: " + status);
    }

    complete(tenant.getId(), tenant.getTier(), false);
  }

  private void runProvisioning(TenantEntity tenant) {
    TenantTier tier = tenant.getTier();
    boolean stubMode = useStubLabels();

    updateStep(tenant.getId(), tier, 0, null, stubMode);
    var idp =
        identityPlatformClient.createTenant(tenant.getSlug(), tenant.getDisplayName());
    tenant = reload(tenant.getId());
    tenant.setIdentityPlatformTenantId(idp.identityPlatformTenantId());
    tenant.setEnabledAuthProvidersJson(provisioningJson.writeProviders(idp.enabledProviders()));
    tenantRepository.save(tenant);

    updateStep(tenant.getId(), tier, 1, null, stubMode);
    TenantDispatchPayload payload = toDispatchPayload(reload(tenant.getId()));

    if (tier == TenantTier.STANDARD) {
      infrastructureProvisioner.triggerStandardTenant(payload);
      updateStep(tenant.getId(), tier, 2, null, stubMode);

      if (stubMode) {
        simulateStandardData(tenant.getId());
        updateStep(tenant.getId(), tier, 3, null, stubMode);
        updateStep(tenant.getId(), tier, 4, null, stubMode);
        complete(tenant.getId(), tier, stubMode);
      } else {
        log.info(
            "Tenant {} awaiting infra workflow completion (use-stubs=false)",
            tenant.getSlug());
      }
      return;
    }

    if (tier == TenantTier.ENTERPRISE) {
      infrastructureProvisioner.triggerEnterpriseTenant(payload);
      updateStep(tenant.getId(), tier, 2, null, stubMode);

      if (!stubMode) {
        log.info(
            "Tenant {} awaiting infra workflow completion (use-stubs=false)",
            tenant.getSlug());
        return;
      }

      updateStep(tenant.getId(), tier, 3, null, stubMode);
      updateStep(tenant.getId(), tier, 4, null, stubMode);
      updateStep(tenant.getId(), tier, 5, null, stubMode);

      tenant = reload(tenant.getId());
      if (tenant.getFirestoreDatabase() == null || tenant.getFirestoreDatabase().isBlank()) {
        tenant.setFirestoreDatabase("(default)-" + tenant.getSlug());
      }
      if (tenant.getGcsBucket() == null || tenant.getGcsBucket().isBlank()) {
        tenant.setGcsBucket(TenantNaming.gcsBucket(tenant.getSlug(), TenantTier.ENTERPRISE));
      }
      tenantRepository.save(tenant);

      updateStep(tenant.getId(), tier, 6, null, stubMode);
      complete(tenant.getId(), tier, stubMode);
    }
  }

  private void simulateStandardData(String tenantId) {
    TenantEntity tenant = reload(tenantId);
    standardDataProvisioner.createDatabase(tenant.getDbName());
    standardDataProvisioner.createSearchIndex(tenant.getSearchIndex());
  }

  private boolean useStubLabels() {
    return platformProperties.getProvisioning().isUseStubs();
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

  private void updateStep(
      String tenantId, TenantTier tier, int doneThrough, String failedKey, boolean stubLabels) {
    TenantEntity tenant = reload(tenantId);
    List<com.tripplanning.platform.tenant.TenantDtos.ProvisioningStepDto> steps =
        tier == TenantTier.ENTERPRISE
            ? ProvisioningStepDefinitions.enterpriseSteps(doneThrough, failedKey, stubLabels)
            : ProvisioningStepDefinitions.standardSteps(doneThrough, failedKey, stubLabels);
    tenant.setProvisioningStepsJson(provisioningJson.writeSteps(steps));
    tenant.setUpdatedAt(Instant.now());
    tenantRepository.save(tenant);
  }

  private void complete(String tenantId, TenantTier tier, boolean stubLabels) {
    TenantEntity tenant = reload(tenantId);
    tenant.setStatus(TenantStatus.ACTIVE);
    tenant.setProvisioningStepsJson(
        provisioningJson.writeSteps(ProvisioningStepDefinitions.completed(tier, stubLabels)));
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
