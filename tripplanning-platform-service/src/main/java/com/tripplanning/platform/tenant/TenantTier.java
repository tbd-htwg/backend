package com.tripplanning.platform.tenant;

public enum TenantTier {
  FREE,
  STANDARD,
  ENTERPRISE,
  /** Local JVM dev tenant: enterprise-capable features on the shared stub stack. */
  DEVELOP;

  public boolean supportsCustomFields() {
    return this == ENTERPRISE || this == DEVELOP;
  }
}
