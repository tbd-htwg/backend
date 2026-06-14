package com.tripplanning.platform.infra;

import com.tripplanning.platform.tenant.TenantEntity;
import com.tripplanning.platform.tenant.TenantNaming;
import com.tripplanning.platform.tenant.TenantTier;

public interface TenantDbCredentialProvider {

  record DbCredentials(String userName, String password) {}

  DbCredentials resolve(TenantEntity tenant);
}
