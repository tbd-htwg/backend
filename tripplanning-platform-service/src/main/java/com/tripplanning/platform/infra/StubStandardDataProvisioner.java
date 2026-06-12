package com.tripplanning.platform.infra;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnExpression("'${tripplanning.platform.standard-postgres.jdbc-url:}'.isEmpty()")
public class StubStandardDataProvisioner implements StandardDataProvisioner {

  @Override
  public void createDatabase(String dbName) {
    log.info("[stub] CREATE DATABASE {}", dbName);
  }

  @Override
  public void createSearchIndex(String indexName) {
    log.info("[stub] Create OpenSearch index {}", indexName);
  }
}
