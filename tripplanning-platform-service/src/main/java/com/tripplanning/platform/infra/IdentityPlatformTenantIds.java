package com.tripplanning.platform.infra;

/** Helpers for Identity Platform tenant IDs stored on {@link com.tripplanning.platform.tenant.TenantEntity}. */
public final class IdentityPlatformTenantIds {

  private static final String STUB_PREFIX = "stub-idp-";

  private IdentityPlatformTenantIds() {}

  /** Stub provisioner IDs are not real GCP tenants — use platform-realm Firebase auth instead. */
  public static String effectiveTenantId(String identityPlatformTenantId) {
    if (identityPlatformTenantId == null || identityPlatformTenantId.isBlank()) {
      return null;
    }
    if (identityPlatformTenantId.startsWith(STUB_PREFIX)) {
      return null;
    }
    return identityPlatformTenantId;
  }

  public static boolean isStubTenantId(String identityPlatformTenantId) {
    return identityPlatformTenantId != null && identityPlatformTenantId.startsWith(STUB_PREFIX);
  }
}
