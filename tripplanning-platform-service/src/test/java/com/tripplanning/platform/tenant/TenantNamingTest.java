package com.tripplanning.platform.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TenantNamingTest {

  private static final String HOST_BASE = "k8s.tbd-htwg.de";
  private static final String ENTERPRISE_BASE = "enterprise.k8s.tbd-htwg.de";

  @Test
  void standardHostAndPaths() {
    assertThat(TenantNaming.hostUrl("acme", TenantTier.STANDARD, HOST_BASE, ENTERPRISE_BASE))
        .isEqualTo("https://acme.k8s.tbd-htwg.de");
    assertThat(TenantNaming.namespace("acme", TenantTier.STANDARD))
        .isEqualTo("tripplanning-standard");
    assertThat(TenantNaming.dbName("acme-corp", TenantTier.STANDARD))
        .isEqualTo("tripplanning_std_acme_corp");
    assertThat(TenantNaming.frontendPath("acme", TenantTier.STANDARD))
        .isEqualTo("/standard/acme/");
  }

  @Test
  void enterpriseHostAndPaths() {
    assertThat(TenantNaming.hostUrl("acme", TenantTier.ENTERPRISE, HOST_BASE, ENTERPRISE_BASE))
        .isEqualTo("https://acme.enterprise.k8s.tbd-htwg.de");
    assertThat(TenantNaming.namespace("acme", TenantTier.ENTERPRISE))
        .isEqualTo("tripplanning-ent-acme");
    assertThat(TenantNaming.dbName("acme", TenantTier.ENTERPRISE))
        .isEqualTo("tripplanning_ent_acme");
    assertThat(TenantNaming.frontendPath("acme", TenantTier.ENTERPRISE))
        .isEqualTo("/enterprise/acme/");
    assertThat(TenantNaming.gcsBucket("acme", TenantTier.ENTERPRISE))
        .isEqualTo("tripplanning-ent-acme-images");
    assertThat(TenantNaming.imageTag("acme", TenantTier.ENTERPRISE)).isEqualTo("enterprise-acme");
  }
}
