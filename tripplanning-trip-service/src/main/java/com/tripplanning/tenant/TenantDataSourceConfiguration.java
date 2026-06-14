package com.tripplanning.tenant;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@ConditionalOnProperty(
    name = "tripplanning.tenant.datasource-routing.enabled",
    havingValue = "true")
@EnableConfigurationProperties(DataSourceProperties.class)
public class TenantDataSourceConfiguration {

  @Bean
  @Primary
  public DataSource dataSource(
      DataSourceProperties properties,
      TenantPlatformClient platformClient,
      TenantSchemaMigrator schemaMigrator) {
    DataSource defaultDataSource = properties.initializeDataSourceBuilder().build();
    return new TenantRoutingDataSource(
        defaultDataSource, properties, platformClient, schemaMigrator);
  }
}
