package com.tripplanning.tenant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.tripplanning.common.tenant.TenantContextHolder;

@Component
public class TenantSearchIndexResolver {

  private final String defaultIndex;
  private final TenantPlatformClient tenantPlatformClient;

  public TenantSearchIndexResolver(
      @Value("${tripplanning.search.elasticsearch-index-name:tripentity}") String defaultIndex,
      TenantPlatformClient tenantPlatformClient) {
    this.defaultIndex = defaultIndex;
    this.tenantPlatformClient = tenantPlatformClient;
  }

  public String currentIndex() {
    String slug = TenantContextHolder.slugOrDefault();
    if ("free".equals(slug)) {
      return defaultIndex;
    }
    return tenantPlatformClient.resolve(slug).searchIndex();
  }
}
