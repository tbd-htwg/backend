package com.tripplanning.common.tenant;

public final class TenantContextHolder {

  private static final ThreadLocal<TenantContext> CURRENT = new ThreadLocal<>();

  private TenantContextHolder() {}

  public static void set(TenantContext context) {
    CURRENT.set(context);
  }

  public static TenantContext get() {
    return CURRENT.get();
  }

  public static String slugOrDefault() {
    TenantContext ctx = CURRENT.get();
    return ctx != null ? ctx.slug() : TenantContext.FREE_SLUG;
  }

  public static void clear() {
    CURRENT.remove();
  }
}
