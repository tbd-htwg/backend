package com.tripplanning.platform.tenant;

public final class TenantNaming {

  private TenantNaming() {}

  public static String hostUrl(String slug, String hostBase) {
    if ("free".equals(slug)) {
      return "https://" + hostBase;
    }
    return "https://" + slug + "." + hostBase;
  }

  public static String namespace(String slug, TenantTier tier) {
    return switch (tier) {
      case FREE -> "tripplanning-free";
      case STANDARD -> "tripplanning-standard";
      case PREMIUM -> "tripplanning-" + slug;
    };
  }

  public static String dbName(String slug, TenantTier tier) {
    return switch (tier) {
      case FREE -> "tripplanning";
      case STANDARD -> "tenant_" + slug.replace('-', '_');
      case PREMIUM -> "tripplanning_" + slug.replace('-', '_');
    };
  }

  public static String searchIndex(String slug, TenantTier tier) {
    return tier == TenantTier.FREE ? "tripentity" : "tripentity-" + slug;
  }

  public static java.math.BigDecimal estimatedCost(TenantTier tier) {
    return switch (tier) {
      case FREE -> java.math.BigDecimal.ZERO;
      case STANDARD -> new java.math.BigDecimal("45");
      case PREMIUM -> new java.math.BigDecimal("180");
    };
  }
}
