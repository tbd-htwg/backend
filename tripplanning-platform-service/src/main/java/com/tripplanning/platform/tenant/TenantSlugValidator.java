package com.tripplanning.platform.tenant;

import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class TenantSlugValidator {

  private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

  private static final Set<String> RESERVED =
      Set.of("free", "develop", "admin", "api", "www", "platform", "gateway", "flux", "default");

  public void validate(String slug) {
    if (slug == null || slug.isBlank()) {
      throw new IllegalArgumentException("Slug is required");
    }
    String normalized = slug.trim().toLowerCase();
    if (!SLUG_PATTERN.matcher(normalized).matches()) {
      throw new IllegalArgumentException(
          "Slug must be lowercase letters, numbers, and hyphens only");
    }
    if (RESERVED.contains(normalized)) {
      throw new IllegalArgumentException("Slug is reserved: " + normalized);
    }
  }
}
