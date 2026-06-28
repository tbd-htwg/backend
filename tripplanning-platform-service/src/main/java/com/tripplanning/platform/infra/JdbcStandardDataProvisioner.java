package com.tripplanning.platform.infra;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.tripplanning.platform.config.PlatformProperties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnProperty(
    name = "tripplanning.platform.provisioning.use-stubs",
    havingValue = "false")
public class JdbcStandardDataProvisioner implements StandardDataProvisioner {

  private final PlatformProperties platformProperties;
  private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

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
    validateIdentifier(indexName);
    String hosts = platformProperties.getOpenSearch().getHosts();
    if (hosts == null || hosts.isBlank()) {
      log.info("[stub] Create OpenSearch index {}", indexName);
      return;
    }
    String protocol = platformProperties.getOpenSearch().getProtocol();
    String host = hosts.split(",")[0].trim();
    String url = protocol + "://" + host + "/" + indexName;
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(10))
              .PUT(HttpRequest.BodyPublishers.ofString("{}"))
              .header("Content-Type", "application/json")
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        log.info("Created OpenSearch index {}", indexName);
        return;
      }
      if (response.statusCode() == 400
          && response.body() != null
          && response.body().toLowerCase().contains("resource_already_exists")) {
        log.warn("OpenSearch index {} already exists", indexName);
        return;
      }
      throw new IllegalStateException(
          "OpenSearch index creation failed for "
              + indexName
              + ": HTTP "
              + response.statusCode()
              + " "
              + response.body());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted creating OpenSearch index " + indexName, e);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to create OpenSearch index " + indexName, e);
    }
  }

  private Connection openAdminConnection() throws SQLException {
    var props = platformProperties.getStandardPostgres();
    return java.sql.DriverManager.getConnection(
        props.getJdbcUrl(), props.getUsername(), props.getPassword());
  }

  private static void validateIdentifier(String name) {
    if (!name.matches("^[a-zA-Z_][a-zA-Z0-9_-]*$")) {
      throw new IllegalArgumentException("Invalid identifier: " + name);
    }
  }
}
