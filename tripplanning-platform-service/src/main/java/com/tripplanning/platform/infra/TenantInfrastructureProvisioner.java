package com.tripplanning.platform.infra;

public interface TenantInfrastructureProvisioner {

  void triggerStandardTenant(TenantDispatchPayload payload);

  void triggerEnterpriseTenant(TenantDispatchPayload payload);
}
