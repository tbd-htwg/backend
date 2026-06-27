package com.tripplanning.common.tenant;

public record TenantContext(
    String slug, String tier, boolean publicTripAccess, boolean publicImageAccess) {

  public static final String FREE_SLUG = "free";

  public TenantContext(String slug, String tier) {
    this(slug, tier, true, true);
  }

  public boolean isFree() {
    return FREE_SLUG.equals(slug);
  }
}
