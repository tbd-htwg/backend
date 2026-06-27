package com.tripplanning.customfield;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.tripplanning.common.tenant.TenantContextHolder;
import com.tripplanning.customfield.dto.CustomFieldDtos.ArchiveCustomFieldRequest;
import com.tripplanning.customfield.dto.CustomFieldDtos.CreateCustomFieldRequest;
import com.tripplanning.customfield.dto.CustomFieldDtos.CustomFieldDeclarationDto;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v2/admin/custom-fields")
@RequiredArgsConstructor
public class AdminCustomFieldController {

  static final String ADMIN_TENANT_SLUG_HEADER = "X-Admin-Tenant-Slug";
  private static final String BEARER_PREFIX = "Bearer ";

  private final FirestoreCustomFieldService firestoreCustomFieldService;
  private final JwtDecoder jwtDecoder;

  @GetMapping
  public List<CustomFieldDeclarationDto> list(
      HttpServletRequest request,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
    requirePlatformAdmin(authorization);
    return firestoreCustomFieldService.listDeclarations(resolveSlug(request), true);
  }

  @PostMapping
  public CustomFieldDeclarationDto create(
      HttpServletRequest request,
      @Valid @RequestBody CreateCustomFieldRequest body,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
    requirePlatformAdmin(authorization);
    String slug = resolveSlug(request);
    return firestoreCustomFieldService.createDeclaration(
        slug,
        CustomFieldValidation.normalizeId(body.id()),
        CustomFieldValidation.normalizeName(body.name()),
        CustomFieldValidation.parseType(body.type()));
  }

  @PatchMapping("/{fieldId}")
  public CustomFieldDeclarationDto archive(
      HttpServletRequest request,
      @PathVariable String fieldId,
      @Valid @RequestBody ArchiveCustomFieldRequest body,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
    requirePlatformAdmin(authorization);
    String slug = resolveSlug(request);
    return firestoreCustomFieldService.setArchived(
        slug, CustomFieldValidation.normalizeId(fieldId), body.archived());
  }

  private static String resolveSlug(HttpServletRequest request) {
    String headerSlug = request.getHeader(ADMIN_TENANT_SLUG_HEADER);
    if (headerSlug != null && !headerSlug.isBlank()) {
      return TenantContextFilter.normalizeSlug(headerSlug);
    }
    return TenantContextHolder.slugOrDefault();
  }

  private void requirePlatformAdmin(String authorization) {
    Jwt jwt = resolveJwt(authorization);
    if (!Boolean.TRUE.equals(jwt.getClaim("platform_admin"))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform admin required");
    }
  }

  private Jwt resolveJwt(String authorization) {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
      return jwt;
    }
    if (authorization == null || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    try {
      return jwtDecoder.decode(authorization.substring(BEARER_PREFIX.length()).trim());
    } catch (RuntimeException e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
    }
  }
}
