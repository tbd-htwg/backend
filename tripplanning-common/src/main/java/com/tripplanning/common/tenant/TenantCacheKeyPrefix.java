package com.tripplanning.common.tenant;

import java.util.List;

import org.springframework.stereotype.Component;

@Component("tenantCacheKeyPrefix")
public class TenantCacheKeyPrefix {

  public String prefix() {
    TenantContext ctx = TenantContextHolder.get();
    if (ctx == null || ctx.isFree()) {
      return "";
    }
    String slug = ctx.slug();
    String tier = ctx.tier();
    if (tier != null && "STANDARD".equalsIgnoreCase(tier)) {
      return "std:" + slug + ":";
    }
    if (tier != null && "ENTERPRISE".equalsIgnoreCase(tier)) {
      return "ent:" + slug + ":";
    }
    return slug + ":";
  }

  public String qualify(String key) {
    return prefix() + key;
  }

  public Object qualifyTrip(long tripId) {
    String p = prefix();
    return p.isEmpty() ? tripId : p + tripId;
  }

  public Object qualifyPage(int page, int size) {
    String p = prefix();
    List<Integer> key = List.of(page, size);
    return p.isEmpty() ? key : p + key;
  }

  public Object qualifyUserPage(long userId, int page, int size) {
    String p = prefix();
    List<Object> key = List.of(userId, page, size);
    return p.isEmpty() ? key : p + key;
  }

  public Object qualifyAll() {
    String p = prefix();
    return p.isEmpty() ? "all" : p + "all";
  }
}
