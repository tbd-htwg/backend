package com.tripplanning.platform.auth;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.tripplanning.platform.config.PlatformProperties;
import com.tripplanning.platform.tenant.PlatformAdminRepository;

@Service
public class PlatformAdminService {

  private final PlatformAdminRepository platformAdminRepository;
  private final Set<String> configuredAdminEmails;

  public PlatformAdminService(
      PlatformAdminRepository platformAdminRepository,
      PlatformProperties platformProperties) {
    this.platformAdminRepository = platformAdminRepository;
    this.configuredAdminEmails =
        Arrays.stream(
                (valueOrEmpty(platformProperties.getBootstrapAdminEmail())
                        + ","
                        + valueOrEmpty(platformProperties.getAdditionalAdminEmails()))
                    .split(","))
            .map(String::trim)
            .filter(email -> !email.isEmpty())
            .map(String::toLowerCase)
            .collect(Collectors.toUnmodifiableSet());
  }

  private static String valueOrEmpty(String value) {
    return value == null ? "" : value;
  }

  public boolean isPlatformAdmin(String email) {
    if (email == null || email.isBlank()) {
      return false;
    }
    String normalized = email.trim().toLowerCase();
    if (configuredAdminEmails.contains(normalized)) {
      return true;
    }
    return platformAdminRepository.existsByEmailIgnoreCase(normalized);
  }
}
