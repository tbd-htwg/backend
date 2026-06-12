package com.tripplanning.platform.infra;

public interface StandardDataProvisioner {

  void createDatabase(String dbName);

  void createSearchIndex(String indexName);
}
