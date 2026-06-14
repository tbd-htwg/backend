package com.tripplanning.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TenantRoutingDataSourceTest {

  @Test
  void withDatabaseNameReplacesSegmentBeforeQuery() {
    String url = "jdbc:postgresql:///tripplanning?cloudSqlInstance=proj:region:inst";
    assertThat(TenantRoutingDataSource.withDatabaseName(url, "tripplanning_std_acme"))
        .isEqualTo("jdbc:postgresql:///tripplanning_std_acme?cloudSqlInstance=proj:region:inst");
  }

  @Test
  void extractDatabaseNameReadsSegmentBeforeQuery() {
    String url = "jdbc:postgresql:///tripplanning?cloudSqlInstance=proj:region:inst";
    assertThat(TenantRoutingDataSource.extractDatabaseName(url)).isEqualTo("tripplanning");
  }
}
