package com.tripplanning.platform.config;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.tripplanning.platform.provisioning.ProvisioningStepDefinitions;
import com.tripplanning.platform.tenant.PlatformAdminEntity;
import com.tripplanning.platform.tenant.PlatformAdminRepository;
import com.tripplanning.platform.tenant.ProvisioningJson;
import com.tripplanning.platform.tenant.TenantEntity;
import com.tripplanning.platform.tenant.TenantNaming;
import com.tripplanning.platform.tenant.TenantRepository;
import com.tripplanning.platform.tenant.TenantStatus;
import com.tripplanning.platform.tenant.TenantTier;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PlatformDataInitializer implements ApplicationRunner {

  private final TenantRepository tenantRepository;
  private final PlatformAdminRepository platformAdminRepository;
  private final ProvisioningJson provisioningJson;
  private final PlatformProperties platformProperties;

  @Override
  public void run(ApplicationArguments args) {
    seedFreeTenant();
    seedBootstrapAdmin();
  }

  private void seedFreeTenant() {
    if (tenantRepository.existsBySlug("free")) {
      return;
    }
    String hostBase = platformProperties.getHostBase();
    Instant now = Instant.now();
    TenantEntity free =
        TenantEntity.builder()
            .id("tenant-free")
            .slug("free")
            .displayName("Free Pool")
            .tier(TenantTier.FREE)
            .status(TenantStatus.ACTIVE)
            .hostUrl(
                TenantNaming.hostUrl(
                    "free",
                    TenantTier.FREE,
                    hostBase,
                    platformProperties.getEnterpriseHostBase()))
            .namespace(TenantNaming.namespace("free", TenantTier.FREE))
            .createdAt(now)
            .updatedAt(now)
            .dbName("tripplanning")
            .searchIndex("tripentity")
            .estimatedMonthlyCostEur(BigDecimal.ZERO)
            .headerTitle("Trip Planner")
            .provisioningStepsJson(
                provisioningJson.writeSteps(ProvisioningStepDefinitions.completed(TenantTier.FREE)))
            .build();
    tenantRepository.save(free);
  }

  private void seedBootstrapAdmin() {
    String email = platformProperties.getBootstrapAdminEmail();
    if (email == null || email.isBlank()) {
      return;
    }
    if (platformAdminRepository.existsByEmailIgnoreCase(email)) {
      return;
    }
    platformAdminRepository.save(new PlatformAdminEntity(email.toLowerCase(), Instant.now()));
  }
}
