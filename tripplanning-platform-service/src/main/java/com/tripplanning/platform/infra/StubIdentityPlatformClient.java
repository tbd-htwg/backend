package com.tripplanning.platform.infra;

import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnProperty(
    name = "tripplanning.platform.provisioning.use-stubs",
    havingValue = "true",
    matchIfMissing = true)
public class StubIdentityPlatformClient implements IdentityPlatformClient {

  @Override
  public TenantAuthConfig createTenant(String slug, String displayName) {
    String tenantId = "stub-idp-" + slug + "-" + UUID.randomUUID().toString().substring(0, 8);
    log.info("[stub] Created Identity Platform tenant {} for slug={}", tenantId, slug);
    return new TenantAuthConfig(tenantId, List.of("google", "password"));
  }

  @Override
  public void enableProviders(String identityPlatformTenantId, List<String> providers) {
    log.info("[stub] Enabled providers {} on IdP tenant {}", providers, identityPlatformTenantId);
  }
}
