package com.tripplanning.customfield;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.tripplanning.common.client.TripServiceClient;
import com.tripplanning.common.tenant.TenantContextHolder;
import com.tripplanning.customfield.dto.CustomFieldDtos.TripCustomFieldValueDto;
import com.tripplanning.customfield.dto.CustomFieldDtos.UpsertTripCustomFieldValuesRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v2/trips/{tripId}/custom-fields")
@RequiredArgsConstructor
public class TripCustomFieldController {

  private static final String BEARER_PREFIX = "Bearer ";

  private final FirestoreCustomFieldService firestoreCustomFieldService;
  private final TripServiceClient tripServiceClient;
  private final JwtDecoder jwtDecoder;

  @GetMapping
  public List<TripCustomFieldValueDto> list(@PathVariable long tripId) {
    ensureTripExists(tripId);
    return firestoreCustomFieldService.listTripValues(TenantContextHolder.slugOrDefault(), tripId);
  }

  @PutMapping
  public List<TripCustomFieldValueDto> upsert(
      @PathVariable long tripId,
      @Valid @RequestBody UpsertTripCustomFieldValuesRequest request,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
    ensureTripExists(tripId);
    long callerId = resolveCallerUserId(authorization);
    if (!tripServiceClient.isTripOwnedBy(tripId, callerId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the trip owner can edit custom fields");
    }
    List<Map.Entry<String, String>> entries =
        request.values() == null
            ? List.of()
            : request.values().stream()
                .map(v -> Map.entry(v.fieldId(), v.value()))
                .toList();
    return firestoreCustomFieldService.upsertTripValues(
        TenantContextHolder.slugOrDefault(), tripId, entries);
  }

  private long resolveCallerUserId(String authorization) {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
      return parseSubject(jwt);
    }
    if (authorization == null || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    try {
      return parseSubject(jwtDecoder.decode(authorization.substring(BEARER_PREFIX.length()).trim()));
    } catch (RuntimeException e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
    }
  }

  private static long parseSubject(Jwt jwt) {
    try {
      return Long.parseLong(jwt.getSubject());
    } catch (NumberFormatException e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token subject");
    }
  }

  private void ensureTripExists(long tripId) {
    if (!tripServiceClient.tripExists(tripId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
    }
  }
}
