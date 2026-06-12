package com.tripplanning.common.tenant;

public record TenantContext(String slug, String tier) {

  public static final String FREE_SLUG = "free";

  public boolean isFree() {
    return FREE_SLUG.equals(slug);
  }
}
