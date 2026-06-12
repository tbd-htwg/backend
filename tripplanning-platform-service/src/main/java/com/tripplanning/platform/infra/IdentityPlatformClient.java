package com.tripplanning.platform.infra;

import java.util.List;

public interface IdentityPlatformClient {

  record TenantAuthConfig(String identityPlatformTenantId, List<String> enabledProviders) {}

  TenantAuthConfig createTenant(String slug, String displayName);

  void enableProviders(String identityPlatformTenantId, List<String> providers);
}
