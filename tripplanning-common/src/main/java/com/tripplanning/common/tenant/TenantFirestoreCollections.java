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

  public static String customFields(String slug) {
    if (slug == null || slug.isBlank() || TenantContext.FREE_SLUG.equals(slug)) {
      return "custom_fields";
    }
    return slug + "_custom_fields";
  }

  public static String tripCustomFieldValues(String slug) {
    if (slug == null || slug.isBlank() || TenantContext.FREE_SLUG.equals(slug)) {
      return "trip_custom_field_values";
    }
    return slug + "_trip_custom_field_values";
  }
}
