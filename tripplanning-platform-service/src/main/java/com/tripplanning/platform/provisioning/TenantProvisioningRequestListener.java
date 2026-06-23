package com.tripplanning.platform.provisioning;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.tripplanning.platform.tenant.TenantProvisioningRequested;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TenantProvisioningRequestListener {

  private final TenantProvisioningService provisioningService;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void provisionAfterCommit(TenantProvisioningRequested event) {
    provisioningService.provision(event.tenantId());
  }
}
