package com.tripplanning.platform.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TenantNamingTest {

  @Test
  void standardObjectPrefixMatchesProvisionedStorageLayout() {
    assertThat(TenantNaming.objectPrefix("firststand", TenantTier.STANDARD))
        .isEqualTo("standard/firststand/");
  }

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
        .isEqualTo("tripplanning");
    assertThat(TenantNaming.dbUser("acme-corp", TenantTier.STANDARD))
        .isEqualTo("tripplanning_app_acme_corp");
    assertThat(TenantNaming.frontendPath("acme", TenantTier.ENTERPRISE))
        .isEqualTo("/enterprise/acme/");
    assertThat(TenantNaming.gcsBucket("acme", TenantTier.ENTERPRISE))
        .isEqualTo("tripplanning-ent-acme-images");
    assertThat(TenantNaming.imageTag("acme", TenantTier.ENTERPRISE)).isEqualTo("enterprise-acme");
  }
}
