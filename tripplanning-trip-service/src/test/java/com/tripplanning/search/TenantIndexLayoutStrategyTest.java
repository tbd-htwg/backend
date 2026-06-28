package com.tripplanning.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TenantIndexLayoutStrategyTest {

  @Test
  void keepsPrimaryTripIndexName() {
    assertThat(
            TenantIndexLayoutStrategy.physicalIndexName(
                "tripentity-firststand", "tripentity"))
        .isEqualTo("tripentity-firststand");
  }

  @Test
  void givesAuxiliaryEntityIndexesDistinctTenantScopedNames() {
    assertThat(
            TenantIndexLayoutStrategy.physicalIndexName(
                "tripentity-firststand", "AccomEntity"))
        .isEqualTo("tripentity-firststand-accomentity");
    assertThat(
            TenantIndexLayoutStrategy.physicalIndexName(
                "tripentity-firststand", "TransportEntity"))
        .isEqualTo("tripentity-firststand-transportentity");
  }
}
