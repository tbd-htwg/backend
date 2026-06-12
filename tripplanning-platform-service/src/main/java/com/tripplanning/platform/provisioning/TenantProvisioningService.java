package com.tripplanning.platform.provisioning;

import java.time.Instant;
import java.util.List;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripplanning.platform.config.PlatformProperties;
import com.tripplanning.platform.infra.DnsClient;
import com.tripplanning.platform.infra.IdentityPlatformClient;
import com.tripplanning.platform.infra.LoadBalancerRegistrar;
import com.tripplanning.platform.infra.PremiumInfrastructureProvisioner;
import com.tripplanning.platform.infra.StandardDataProvisioner;
import com.tripplanning.platform.tenant.ProvisioningJson;
import com.tripplanning.platform.tenant.TenantEntity;
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
  private final PlatformProperties platformProperties;
  private final IdentityPlatformClient identityPlatformClient;
  private final DnsClient dnsClient;
  private final LoadBalancerRegistrar loadBalancerRegistrar;
  private final StandardDataProvisioner standardDataProvisioner;
  private final PremiumInfrastructureProvisioner premiumInfrastructureProvisioner;

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
    String hostBase = platformProperties.getHostBase();
    TenantTier tier = tenant.getTier();

    updateStep(tenant.getId(), tier, 0, null);
    var idp =
        identityPlatformClient.createTenant(tenant.getSlug(), tenant.getDisplayName());
    tenant = reload(tenant.getId());
    tenant.setIdentityPlatformTenantId(idp.identityPlatformTenantId());
    tenant.setEnabledAuthProvidersJson(provisioningJson.writeProviders(idp.enabledProviders()));
    tenantRepository.save(tenant);

    if (tier == TenantTier.STANDARD) {
      dnsClient.registerStandardSubdomain(tenant.getSlug(), hostBase);
      loadBalancerRegistrar.registerStandardHost(tenant.getSlug(), hostBase);

      updateStep(tenant.getId(), tier, 1, null);
      standardDataProvisioner.createDatabase(tenant.getDbName());

      updateStep(tenant.getId(), tier, 2, null);
      standardDataProvisioner.createSearchIndex(tenant.getSearchIndex());

      complete(tenant.getId(), tier);
      return;
    }

    if (tier == TenantTier.PREMIUM) {
      updateStep(tenant.getId(), tier, 1, null);
      dnsClient.registerPremiumSubdomain(tenant.getSlug(), hostBase);
      loadBalancerRegistrar.registerPremiumHost(tenant.getSlug(), hostBase);
      tenant = reload(tenant.getId());
      tenant.setFrontendPath("/" + tenant.getSlug() + "/");
      tenant.setImageTag("premium-" + tenant.getSlug());
      tenantRepository.save(tenant);

      updateStep(tenant.getId(), tier, 2, null);
      premiumInfrastructureProvisioner.triggerPremiumTenant(
          tenant.getSlug(), tenant.getDisplayName());

      // Backing services in the tenant namespace — stubs until infra workflow reports ready.
      updateStep(tenant.getId(), tier, 3, null);
      updateStep(tenant.getId(), tier, 4, null);

      updateStep(tenant.getId(), tier, 5, null);
      tenant = reload(tenant.getId());
      tenant.setFirestoreDatabase("(default)-" + tenant.getSlug());
      tenant.setGcsBucket("tbd-cloudappdev-images-" + tenant.getSlug());
      tenantRepository.save(tenant);

      complete(tenant.getId(), tier);
    }
  }

  private void updateStep(String tenantId, TenantTier tier, int doneThrough, String failedKey) {
    TenantEntity tenant = reload(tenantId);
    List<com.tripplanning.platform.tenant.TenantDtos.ProvisioningStepDto> steps =
        tier == TenantTier.PREMIUM
            ? ProvisioningStepDefinitions.premiumSteps(doneThrough, failedKey)
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
