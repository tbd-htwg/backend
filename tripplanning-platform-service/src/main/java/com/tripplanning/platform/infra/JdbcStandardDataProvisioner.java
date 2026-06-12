package com.tripplanning.platform.infra;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import com.tripplanning.platform.config.PlatformProperties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnExpression(
    "'${tripplanning.platform.standard-postgres.jdbc-url:}'.length() > 0")
public class JdbcStandardDataProvisioner implements StandardDataProvisioner {

  private final PlatformProperties platformProperties;

  public JdbcStandardDataProvisioner(PlatformProperties platformProperties) {
    this.platformProperties = platformProperties;
  }

  @Override
  public void createDatabase(String dbName) {
    validateIdentifier(dbName);
    try (Connection conn = openAdminConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE DATABASE " + dbName);
      log.info("Created database {}", dbName);
    } catch (SQLException e) {
      if (e.getMessage() != null && e.getMessage().toLowerCase().contains("already exists")) {
        log.warn("Database {} already exists", dbName);
        return;
      }
      throw new IllegalStateException("Failed to create database " + dbName, e);
    }
  }

  @Override
  public void createSearchIndex(String indexName) {
    log.info(
        "Search index {} must be created via OpenSearch API (stub logs only until wired)",
        indexName);
  }

  private Connection openAdminConnection() throws SQLException {
    var props = platformProperties.getStandardPostgres();
    return java.sql.DriverManager.getConnection(
        props.getJdbcUrl(), props.getUsername(), props.getPassword());
  }

  private static void validateIdentifier(String name) {
    if (!name.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
      throw new IllegalArgumentException("Invalid SQL identifier: " + name);
    }
  }
}
