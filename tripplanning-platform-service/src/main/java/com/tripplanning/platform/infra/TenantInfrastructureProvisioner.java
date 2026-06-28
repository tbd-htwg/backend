package com.tripplanning.platform.infra;

import java.util.Map;

public interface TenantInfrastructureProvisioner {

  void triggerStandardTenant(TenantDispatchPayload payload);

  void triggerEnterpriseTenant(TenantDispatchPayload payload);

  void updateEnterpriseTenantResources(String slug, Map<String, Object> resourceProfile);
}
