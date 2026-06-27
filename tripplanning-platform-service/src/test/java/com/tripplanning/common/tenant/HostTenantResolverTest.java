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
  void resolvesDevelopFromLocalhost() {
    assertThat(HostTenantResolver.resolveSlug("localhost:5173", HOST_BASE, ENTERPRISE_BASE))
        .isEqualTo("develop");
    assertThat(HostTenantResolver.resolveSlug("127.0.0.1:8080", HOST_BASE, ENTERPRISE_BASE))
        .isEqualTo("develop");
  }

  @Test
  void developTierFromSlug() {
    assertThat(
            HostTenantResolver.tierForSlug(
                "develop", "localhost:5173", ENTERPRISE_BASE))
        .isEqualTo("DEVELOP");
  }

  @Test
  void unknownHostFallsBackToFree() {
    assertThat(HostTenantResolver.resolveSlug("example.com", HOST_BASE, ENTERPRISE_BASE))
        .isEqualTo("free");
  }
}
