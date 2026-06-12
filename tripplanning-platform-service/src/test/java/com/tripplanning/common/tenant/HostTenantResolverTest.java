package com.tripplanning.common.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HostTenantResolverTest {

  private static final String HOST_BASE = "k8s.tbd-htwg.de";
  private static final String ENTERPRISE_BASE = "enterprise.k8s.tbd-htwg.de";

  @Test
  void resolvesFreeFromApexHost() {
    assertThat(HostTenantResolver.resolveSlug(HOST_BASE, HOST_BASE, ENTERPRISE_BASE))
        .isEqualTo("free");
  }

  @Test
  void resolvesStandardSubdomain() {
    assertThat(
            HostTenantResolver.resolveSlug(
                "acme." + HOST_BASE, HOST_BASE, ENTERPRISE_BASE))
        .isEqualTo("acme");
  }

  @Test
  void resolvesEnterpriseSubdomain() {
    assertThat(
            HostTenantResolver.resolveSlug(
                "acme." + ENTERPRISE_BASE, HOST_BASE, ENTERPRISE_BASE))
        .isEqualTo("acme");
  }

  @Test
  void enterpriseHostDetection() {
    assertThat(HostTenantResolver.isEnterpriseHost("acme." + ENTERPRISE_BASE, ENTERPRISE_BASE))
        .isTrue();
    assertThat(HostTenantResolver.isEnterpriseHost("acme." + HOST_BASE, ENTERPRISE_BASE))
        .isFalse();
  }

  @Test
  void unknownHostFallsBackToFree() {
    assertThat(HostTenantResolver.resolveSlug("example.com", HOST_BASE, ENTERPRISE_BASE))
        .isEqualTo("free");
  }
}
