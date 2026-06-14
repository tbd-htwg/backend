package com.tripplanning.tenant;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/** Runs Flyway migrations once per tenant database when routing creates a new pool. */
@Component
@Slf4j
public class TenantSchemaMigrator {

  private final Set<String> migratedDatabases = ConcurrentHashMap.newKeySet();

  public void migrateIfNeeded(String dbName, DataSource dataSource) {
    if (!migratedDatabases.add(dbName)) {
      return;
    }
    try {
      Flyway.configure()
          .dataSource(dataSource)
          .locations("classpath:db/migration")
          .load()
          .migrate();
      log.info("Applied Flyway migrations to tenant database {}", dbName);
    } catch (Exception e) {
      migratedDatabases.remove(dbName);
      throw new IllegalStateException("Failed to migrate tenant database " + dbName, e);
    }
  }
}
