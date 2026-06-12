package com.tripplanning.platform.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.tripplanning.platform.tenant.PlatformAdminRepository;

@Service
public class PlatformAdminService {

  private final PlatformAdminRepository platformAdminRepository;
  private final String bootstrapAdminEmail;

  public PlatformAdminService(
      PlatformAdminRepository platformAdminRepository,
      @Value("${tripplanning.platform.bootstrap-admin-email:admin@platform.demo}")
          String bootstrapAdminEmail) {
    this.platformAdminRepository = platformAdminRepository;
    this.bootstrapAdminEmail = bootstrapAdminEmail.trim().toLowerCase();
  }

  public boolean isPlatformAdmin(String email) {
    if (email == null || email.isBlank()) {
      return false;
    }
    String normalized = email.trim().toLowerCase();
    if (normalized.equals(bootstrapAdminEmail)) {
      return true;
    }
    return platformAdminRepository.existsByEmailIgnoreCase(normalized);
  }
}
