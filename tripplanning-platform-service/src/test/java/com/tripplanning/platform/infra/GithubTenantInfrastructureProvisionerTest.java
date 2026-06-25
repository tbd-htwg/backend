package com.tripplanning.platform.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.tripplanning.platform.tenant.TenantTier;

class GithubTenantInfrastructureProvisionerTest {

  @Test
  void standardDispatchContainsOnlyWorkflowInputs() {
    Map<String, Object> payload =
        GithubTenantInfrastructureProvisioner.buildClientPayload(
            tenantPayload(TenantTier.STANDARD, null));

    assertThat(payload)
        .containsExactly(
            Map.entry("slug", "acme"),
            Map.entry("displayName", "Acme Travel"),
            Map.entry("tier", "STANDARD"),
            Map.entry("identityPlatformTenantId", "idp-acme"));
    assertThat(payload).hasSizeLessThanOrEqualTo(10);
  }

  @Test
  void enterpriseDispatchStaysWithinGithubPropertyLimit() {
    Map<String, Object> payload =
        GithubTenantInfrastructureProvisioner.buildClientPayload(
            tenantPayload(TenantTier.ENTERPRISE, "latest"));

    assertThat(payload)
        .containsExactly(
            Map.entry("slug", "acme"),
            Map.entry("displayName", "Acme Travel"),
            Map.entry("tier", "ENTERPRISE"),
            Map.entry("identityPlatformTenantId", "idp-acme"),
            Map.entry("imageTag", "latest"));
    assertThat(payload).hasSizeLessThanOrEqualTo(10);
    assertThat(payload)
        .doesNotContainKeys(
            "hostUrl", "namespace", "dbName", "searchIndex", "frontendPath", "gcsBucket");
  }

  private static TenantDispatchPayload tenantPayload(TenantTier tier, String imageTag) {
    return new TenantDispatchPayload(
        "acme",
        "Acme Travel",
        tier,
        "idp-acme",
        imageTag);
  }
}
