package com.tripplanning.platform.tenant;

import com.tripplanning.common.tenant.HostTenantResolver;

public final class TenantNaming {

  private TenantNaming() {}

  public static String hostUrl(String slug, String hostBase) {
    return hostUrl(slug, TenantTier.STANDARD, hostBase, HostTenantResolver.DEFAULT_ENTERPRISE_HOST_BASE);
  }

  public static String hostUrl(
      String slug, TenantTier tier, String hostBase, String enterpriseHostBase) {
    if (tier == TenantTier.FREE || "free".equals(slug)) {
      return "https://" + hostBase;
    }
    if (tier == TenantTier.ENTERPRISE) {
      return "https://" + slug + "." + enterpriseHostBase;
    }
    return "https://" + slug + "." + hostBase;
  }

  public static String namespace(String slug, TenantTier tier) {
    return switch (tier) {
      case FREE -> "tripplanning-free";
      case STANDARD -> "tripplanning-standard";
      case ENTERPRISE -> "tripplanning-ent-" + slug;
    };
  }

  public static String dbName(String slug, TenantTier tier) {
    return switch (tier) {
      case FREE -> "tripplanning";
      case STANDARD -> "tripplanning_std_" + slug.replace('-', '_');
      case ENTERPRISE -> "tripplanning_ent_" + slug.replace('-', '_');
    };
  }

  public static String searchIndex(String slug, TenantTier tier) {
    return tier == TenantTier.FREE ? "tripentity" : "tripentity-" + slug;
  }

  public static String frontendPath(String slug, TenantTier tier) {
    return switch (tier) {
      case STANDARD -> "/standard/" + slug + "/";
      case ENTERPRISE -> "/enterprise/" + slug + "/";
      default -> "/";
    };
  }

  public static String gcsBucket(String slug, TenantTier tier) {
    return tier == TenantTier.ENTERPRISE ? "tripplanning-ent-" + slug + "-images" : null;
  }

  public static String imageTag(String slug, TenantTier tier) {
    return tier == TenantTier.ENTERPRISE ? "enterprise-" + slug : null;
  }

  public static java.math.BigDecimal estimatedCost(TenantTier tier) {
    return switch (tier) {
      case FREE -> java.math.BigDecimal.ZERO;
      case STANDARD -> new java.math.BigDecimal("45");
      case ENTERPRISE -> new java.math.BigDecimal("180");
    };
  }
}
