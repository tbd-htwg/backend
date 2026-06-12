package com.tripplanning.tenant;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import com.tripplanning.common.tenant.TenantContextHolder;

/**
 * Routes JDBC connections to per-tenant databases on the shared Standard Postgres instance.
 * Free tier uses the default datasource URL; Standard/Premium tenants use {@code dbName} from
 * platform-service (or naming conventions when platform is unavailable).
 */
public class TenantRoutingDataSource extends AbstractRoutingDataSource {

  private final DataSource defaultDataSource;
  private final String defaultDbName;
  private final DataSourceProperties properties;
  private final TenantPlatformClient platformClient;
  private final Map<String, DataSource> pools = new ConcurrentHashMap<>();

  public TenantRoutingDataSource(
      DataSource defaultDataSource,
      DataSourceProperties properties,
      TenantPlatformClient platformClient) {
    this.defaultDataSource = defaultDataSource;
    this.properties = properties;
    this.platformClient = platformClient;
    this.defaultDbName = extractDatabaseName(properties.determineUrl());
    setDefaultTargetDataSource(defaultDataSource);
    setTargetDataSources(Map.of());
    afterPropertiesSet();
  }

  @Override
  protected Object determineCurrentLookupKey() {
    return currentDbName();
  }

  @Override
  protected DataSource determineTargetDataSource() {
    String dbName = currentDbName();
    if (dbName.equals(defaultDbName)) {
      return defaultDataSource;
    }
    return pools.computeIfAbsent(dbName, this::createPool);
  }

  private String currentDbName() {
    String slug = TenantContextHolder.slugOrDefault();
    return platformClient.resolve(slug).dbName();
  }

  private DataSource createPool(String dbName) {
    String url = withDatabaseName(properties.determineUrl(), dbName);
    return properties
        .initializeDataSourceBuilder()
        .url(url)
        .build();
  }

  static String extractDatabaseName(String url) {
    if (url == null || url.isBlank()) {
      return "tripplanning";
    }
    int slash = url.lastIndexOf('/');
    if (slash < 0) {
      return "tripplanning";
    }
    int query = url.indexOf('?', slash);
    if (query < 0) {
      return url.substring(slash + 1);
    }
    return url.substring(slash + 1, query);
  }

  static String withDatabaseName(String url, String dbName) {
    int slash = url.lastIndexOf('/');
    if (slash < 0) {
      return url;
    }
    int query = url.indexOf('?', slash);
    if (query < 0) {
      return url.substring(0, slash + 1) + dbName;
    }
    return url.substring(0, slash + 1) + dbName + url.substring(query);
  }
}
