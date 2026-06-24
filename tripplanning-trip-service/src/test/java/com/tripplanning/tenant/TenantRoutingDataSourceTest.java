package com.tripplanning.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;

import com.tripplanning.common.tenant.TenantContextHolder;

class TenantRoutingDataSourceTest {

  @Test
  void usesConfiguredDefaultDatabaseWhenNoTenantContextExists() {
    DataSourceProperties properties = new DataSourceProperties();
    properties.setUrl("jdbc:postgresql://localhost:5432/tripplanning_std_firststand");
    properties.setUsername("tripplanning_app_firststand");
    properties.setDriverClassName("org.postgresql.Driver");

    TenantRoutingDataSource dataSource =
        new TenantRoutingDataSource(
            properties.initializeDataSourceBuilder().build(),
            properties,
            new TenantPlatformClient("http://localhost:8083", ""),
            new TenantSchemaMigrator());

    TenantContextHolder.clear();
    assertThat(dataSource.currentDbName()).isEqualTo("tripplanning_std_firststand");
  }

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
