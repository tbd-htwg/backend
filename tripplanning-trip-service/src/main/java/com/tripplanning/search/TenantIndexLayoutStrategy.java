package com.tripplanning.search;

import java.util.Locale;

import org.hibernate.search.backend.elasticsearch.index.layout.IndexLayoutStrategy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.tripplanning.tenant.TenantSearchIndexResolver;

/**
 * Routes Hibernate Search writes/reads to per-tenant OpenSearch indices when datasource routing is
 * enabled. Uses the logical index name {@code tripentity} and maps it to {@code tripentity-{slug}}.
 */
@Component("tenantIndexLayoutStrategy")
@ConditionalOnProperty(name = "tripplanning.tenant.datasource-routing.enabled", havingValue = "true")
public class TenantIndexLayoutStrategy implements IndexLayoutStrategy {

  private final TenantSearchIndexResolver indexResolver;

  public TenantIndexLayoutStrategy(TenantSearchIndexResolver indexResolver) {
    this.indexResolver = indexResolver;
  }

  @Override
  public String createInitialElasticsearchIndexName(String hibernateSearchIndexName) {
    return resolvePhysicalIndex(hibernateSearchIndexName) + "-000001";
  }

  @Override
  public String createWriteAlias(String hibernateSearchIndexName) {
    return resolvePhysicalIndex(hibernateSearchIndexName) + "-write";
  }

  @Override
  public String createReadAlias(String hibernateSearchIndexName) {
    return resolvePhysicalIndex(hibernateSearchIndexName) + "-read";
  }

  @Override
  public String extractUniqueKeyFromHibernateSearchIndexName(String hibernateSearchIndexName) {
    return hibernateSearchIndexName;
  }

  private String resolvePhysicalIndex(String hibernateSearchIndexName) {
    String tenantIndex = indexResolver.currentIndex();
    if (tenantIndex == null || tenantIndex.isBlank()) {
      return hibernateSearchIndexName;
    }
    return physicalIndexName(tenantIndex, hibernateSearchIndexName);
  }

  static String physicalIndexName(String tenantIndex, String hibernateSearchIndexName) {
    if ("tripentity".equalsIgnoreCase(hibernateSearchIndexName)) {
      return tenantIndex;
    }
    return tenantIndex + "-" + hibernateSearchIndexName.toLowerCase(Locale.ROOT);
  }
}
