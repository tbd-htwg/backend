package com.tripplanning.trip.read;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import com.tripplanning.common.tenant.TenantContext;
import com.tripplanning.common.tenant.TenantContextHolder;
import com.tripplanning.trip.read.TripFeedCachedReader.TripFeedDetailRaw;

public final class TripAccessHelper {

  private TripAccessHelper() {}

  public static void requirePublicTripAccessOrAuth(Authentication authentication) {
    TenantContext ctx = TenantContextHolder.get();
    if (ctx != null && !ctx.publicTripAccess()) {
      if (authentication == null || !authentication.isAuthenticated()) {
        throw new ResponseStatusException(
            HttpStatus.UNAUTHORIZED, "Login required to view trips on this tenant");
      }
    }
  }

  public static Long viewerUserId(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return null;
    }
    if (authentication.getPrincipal() instanceof Jwt jwt) {
      return Long.parseLong(jwt.getSubject());
    }
    return null;
  }

  public static void assertTripReadable(TripFeedDetailRaw raw, Long viewerUserId) {
    if (raw.visible()) {
      return;
    }
    if (viewerUserId != null && viewerUserId == raw.author().id()) {
      return;
    }
    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
  }
}
