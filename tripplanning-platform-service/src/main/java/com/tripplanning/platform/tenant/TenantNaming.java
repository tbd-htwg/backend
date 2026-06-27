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
    if (tier == TenantTier.DEVELOP) {
      return "http://localhost";
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
      case ENTERPRISE, DEVELOP -> "tripplanning-ent-" + slug;
    };
  }

  public static String dbName(String slug, TenantTier tier) {
    return switch (tier) {
      case FREE, ENTERPRISE, DEVELOP -> "tripplanning";
      case STANDARD -> "tripplanning_std_" + slug.replace('-', '_');
    };
  }

  public static String dbUser(String slug, TenantTier tier) {
    return switch (tier) {
      case FREE, ENTERPRISE, DEVELOP -> "tripplanning_app";
      case STANDARD -> "tripplanning_app_" + slug.replace('-', '_');
    };
  }

  public static String dbPasswordSecretId(String slug, TenantTier tier) {
    return switch (tier) {
      case STANDARD -> "tripplanning-standard-" + slug + "-db-password";
      case ENTERPRISE -> "tripplanning-enterprise-" + slug + "-db-password";
      default -> null;
    };
  }

  public static String searchIndex(String slug, TenantTier tier) {
    return tier == TenantTier.FREE ? "tripentity" : "tripentity-" + slug;
  }

  public static String frontendPath(String slug, TenantTier tier) {
    return switch (tier) {
      case STANDARD -> "/standard/" + slug + "/";
      case ENTERPRISE, DEVELOP -> "/enterprise/" + slug + "/";
      default -> "/";
    };
  }

  public static String gcsBucket(String slug, TenantTier tier) {
    return tier == TenantTier.ENTERPRISE || tier == TenantTier.DEVELOP
        ? "tripplanning-ent-" + slug + "-images"
        : null;
  }

  /** Object key prefix within a shared bucket (Standard tier). */
  public static String objectPrefix(String slug, TenantTier tier) {
    if (tier == TenantTier.STANDARD) {
      return "standard/" + slug + "/";
    }
    return "";
  }

  public static String imageTag(String slug, TenantTier tier) {
    // The backend image workflow publishes immutable commit-SHA tags and
    // "latest". A per-tenant tag is only valid after a separate custom image
    // build, which the admin tenant-creation flow does not perform.
    return tier == TenantTier.ENTERPRISE || tier == TenantTier.DEVELOP ? "latest" : null;
  }

  public static java.math.BigDecimal estimatedCost(TenantTier tier) {
    return switch (tier) {
      case FREE, DEVELOP -> java.math.BigDecimal.ZERO;
      case STANDARD -> new java.math.BigDecimal("45");
      case ENTERPRISE -> new java.math.BigDecimal("180");
    };
  }
}
