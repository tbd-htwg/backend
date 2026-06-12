package com.tripplanning.platform.provisioning;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.tripplanning.platform.tenant.TenantTier;

class ProvisioningStepDefinitionsTest {

  @Test
  void standardHasFiveSteps() {
    var steps = ProvisioningStepDefinitions.standardSteps(0, null);
    assertThat(steps).hasSize(5);
    assertThat(steps.get(0).key()).isEqualTo("registry");
    assertThat(steps.get(2).key()).isEqualTo("terraform_infra");
    assertThat(steps.get(4).key()).isEqualTo("search_index");
  }

  @Test
  void enterpriseHasSevenSteps() {
    var steps = ProvisioningStepDefinitions.enterpriseSteps(0, null);
    assertThat(steps).hasSize(7);
    assertThat(steps.get(1).key()).isEqualTo("identity_platform");
    assertThat(steps.get(2).key()).isEqualTo("terraform_infra");
    assertThat(steps.get(6).key()).isEqualTo("gcp_resources");
  }

  @Test
  void completedMarksAllDone() {
    assertThat(ProvisioningStepDefinitions.completed(TenantTier.STANDARD))
        .allMatch(s -> "done".equals(s.status()));
    assertThat(ProvisioningStepDefinitions.completed(TenantTier.ENTERPRISE))
        .allMatch(s -> "done".equals(s.status()));
  }
}
