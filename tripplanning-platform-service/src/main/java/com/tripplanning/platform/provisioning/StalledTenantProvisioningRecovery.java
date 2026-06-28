package com.tripplanning.platform.provisioning;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.tripplanning.platform.tenant.TenantRepository;
import com.tripplanning.platform.tenant.TenantStatus;
import com.tripplanning.platform.tenant.ProvisioningJson;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class StalledTenantProvisioningRecovery {

  private final TenantRepository tenantRepository;
  private final TenantProvisioningService provisioningService;
  private final ProvisioningJson provisioningJson;

  @Async
  @EventListener(ApplicationReadyEvent.class)
  public void recoverProvisioningTenants() {
    var tenants =
        tenantRepository.findByStatusOrderByCreatedAtAsc(TenantStatus.PROVISIONING).stream()
            .filter(
                tenant ->
                    provisioningJson.readSteps(tenant.getProvisioningStepsJson()).stream()
                        .noneMatch(
                            step ->
                                "terraform_infra".equals(step.key())
                                    && "running".equals(step.status())))
            .toList();
    if (!tenants.isEmpty()) {
      log.warn("Recovering {} tenant(s) left in PROVISIONING", tenants.size());
    }
    tenants.forEach(tenant -> provisioningService.provision(tenant.getId()));
  }
}
