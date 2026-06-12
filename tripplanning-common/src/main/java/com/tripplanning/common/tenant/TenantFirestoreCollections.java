package com.tripplanning.common.tenant;

public final class TenantFirestoreCollections {

  private TenantFirestoreCollections() {}

  public static String comments(String slug) {
    if (slug == null || slug.isBlank() || TenantContext.FREE_SLUG.equals(slug)) {
      return "comments";
    }
    return slug + "_comments";
  }

  public static String likes(String slug) {
    if (slug == null || slug.isBlank() || TenantContext.FREE_SLUG.equals(slug)) {
      return "likes";
    }
    return slug + "_likes";
  }
}
