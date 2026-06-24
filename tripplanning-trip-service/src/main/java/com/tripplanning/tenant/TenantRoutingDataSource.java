package com.tripplanning.tenant;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import com.tripplanning.common.tenant.TenantContextHolder;

/**
 * Routes JDBC connections to per-tenant databases on the shared Standard Postgres instance.
 * Free tier uses the default datasource URL; Standard/Enterprise tenants use {@code dbName} from
 * platform-service (or naming conventions when platform is unavailable).
 */
public class TenantRoutingDataSource extends AbstractRoutingDataSource {

  private final DataSource defaultDataSource;
  private final String defaultDbName;
  private final DataSourceProperties properties;
  private final TenantPlatformClient platformClient;
  private final TenantSchemaMigrator schemaMigrator;
  private final Map<String, DataSource> pools = new ConcurrentHashMap<>();

  public TenantRoutingDataSource(
      DataSource defaultDataSource,
      DataSourceProperties properties,
      TenantPlatformClient platformClient,
      TenantSchemaMigrator schemaMigrator) {
    this.defaultDataSource = defaultDataSource;
    this.properties = properties;
    this.platformClient = platformClient;
    this.schemaMigrator = schemaMigrator;
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

  String currentDbName() {
    if (TenantContextHolder.get() == null
        || "free".equals(TenantContextHolder.slugOrDefault())) {
      return defaultDbName;
    }
    String slug = TenantContextHolder.slugOrDefault();
    return platformClient.resolve(slug).dbName();
  }

  private DataSource createPool(String dbName) {
    String slug = TenantContextHolder.slugOrDefault();
    TenantPlatformClient.TenantRuntime runtime = platformClient.resolve(slug);
    String url = withDatabaseName(properties.determineUrl(), dbName);
    DataSource dataSource =
        properties
            .initializeDataSourceBuilder()
            .url(url)
            .username(resolveUsername(runtime))
            .password(resolvePassword(runtime))
            .build();
    schemaMigrator.migrateIfNeeded(dbName, dataSource);
    return dataSource;
  }

  private static String resolveUsername(TenantPlatformClient.TenantRuntime runtime) {
    return runtime.dbUser() != null && !runtime.dbUser().isBlank()
        ? runtime.dbUser()
        : "tripplanning_app";
  }

  private static String resolvePassword(TenantPlatformClient.TenantRuntime runtime) {
    return runtime.dbPassword() != null ? runtime.dbPassword() : "";
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
