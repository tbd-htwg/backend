package com.tripplanning.platform.config;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.tripplanning.common.tenant.HostTenantResolver;
import com.tripplanning.platform.infra.IdentityPlatformTenantIds;
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
  private final Environment environment;

  @Override
  public void run(ApplicationArguments args) {
    seedFreeTenant();
    if (isLocalProfile()) {
      seedDevelopTenant();
    }
    seedBootstrapAdmin();
  }

  private boolean isLocalProfile() {
    return java.util.Arrays.asList(environment.getActiveProfiles()).contains("local");
  }

  private void seedFreeTenant() {
    if (tenantRepository.existsBySlug("free")) {
      upgradeFreeTenantAuthProviders();
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
            .titleRetractToInitials(true)
            .invertHeaderIcon(true)
            .enabledAuthProvidersJson(
                provisioningJson.writeProviders(java.util.List.of("google", "password")))
            .provisioningStepsJson(
                provisioningJson.writeSteps(ProvisioningStepDefinitions.completed(TenantTier.FREE)))
            .build();
    tenantRepository.save(free);
  }

  private void upgradeFreeTenantAuthProviders() {
    tenantRepository
        .findBySlug("free")
        .ifPresent(
            free -> {
              boolean changed = false;
              var providers = provisioningJson.readProviders(free.getEnabledAuthProvidersJson());
              if (providers.size() == 1 && providers.contains("password")) {
                free.setEnabledAuthProvidersJson(
                    provisioningJson.writeProviders(java.util.List.of("google", "password")));
                changed = true;
              }
              if (!free.isTitleRetractToInitials()) {
                free.setTitleRetractToInitials(true);
                changed = true;
              }
              if (!free.isInvertHeaderIcon()) {
                free.setInvertHeaderIcon(true);
                changed = true;
              }
              if (changed) {
                tenantRepository.save(free);
              }
            });
  }

  private void seedDevelopTenant() {
    if (tenantRepository.existsBySlug(HostTenantResolver.DEVELOP_SLUG)) {
      upgradeDevelopTenantForLocalStubAuth();
      return;
    }
    String slug = HostTenantResolver.DEVELOP_SLUG;
    String hostBase = platformProperties.getHostBase();
    Instant now = Instant.now();
    boolean stubLabels = platformProperties.getProvisioning().isUseStubs();
    TenantEntity develop =
        TenantEntity.builder()
            .id("tenant-develop")
            .slug(slug)
            .displayName("Local Development")
            .tier(TenantTier.DEVELOP)
            .status(TenantStatus.ACTIVE)
            .hostUrl(
                TenantNaming.hostUrl(
                    slug,
                    TenantTier.DEVELOP,
                    hostBase,
                    platformProperties.getEnterpriseHostBase()))
            .namespace(TenantNaming.namespace(slug, TenantTier.DEVELOP))
            .createdAt(now)
            .updatedAt(now)
            .dbName(TenantNaming.dbName(slug, TenantTier.DEVELOP))
            .searchIndex(TenantNaming.searchIndex(slug, TenantTier.DEVELOP))
            .firestoreDatabase("(default)-develop")
            .gcsBucket(TenantNaming.gcsBucket(slug, TenantTier.DEVELOP))
            .frontendPath(TenantNaming.frontendPath(slug, TenantTier.DEVELOP))
            .imageTag(TenantNaming.imageTag(slug, TenantTier.DEVELOP))
            .estimatedMonthlyCostEur(TenantNaming.estimatedCost(TenantTier.DEVELOP))
            .headerTitle("Trip Planner (Dev)")
            .enabledAuthProvidersJson(
                provisioningJson.writeProviders(java.util.List.of("google", "password")))
            .provisioningStepsJson(
                provisioningJson.writeSteps(
                    ProvisioningStepDefinitions.completed(TenantTier.DEVELOP, stubLabels)))
            .build();
    tenantRepository.save(develop);
  }

  /** Local stub mode uses the shared Firebase project realm, not fake stub-idp-* tenant IDs. */
  private void upgradeDevelopTenantForLocalStubAuth() {
    if (!platformProperties.getProvisioning().isUseStubs()) {
      return;
    }
    tenantRepository
        .findBySlug(HostTenantResolver.DEVELOP_SLUG)
        .ifPresent(
            develop -> {
              if (IdentityPlatformTenantIds.isStubTenantId(develop.getIdentityPlatformTenantId())) {
                develop.setIdentityPlatformTenantId(null);
                tenantRepository.save(develop);
              }
            });
  }

  private void seedBootstrapAdmin() {
    Arrays.stream(
            (valueOrEmpty(platformProperties.getBootstrapAdminEmail())
                    + ","
                    + valueOrEmpty(platformProperties.getAdditionalAdminEmails()))
                .split(","))
        .map(String::trim)
        .filter(email -> !email.isEmpty())
        .map(String::toLowerCase)
        .filter(email -> !platformAdminRepository.existsByEmailIgnoreCase(email))
        .forEach(
            email ->
                platformAdminRepository.save(new PlatformAdminEntity(email, Instant.now())));
  }

  private static String valueOrEmpty(String value) {
    return value == null ? "" : value;
  }
}
