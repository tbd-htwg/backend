package com.tripplanning.platform.infra;

public interface PremiumInfrastructureProvisioner {

  void triggerPremiumTenant(String slug, String displayName);
}
