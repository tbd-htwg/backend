package com.tripplanning.platform.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripplanning.platform.config.PlatformProperties;

@RestController
@RequestMapping("/api/v2/admin/platform-info")
public class AdminPlatformController {

  private final PlatformProperties platformProperties;

  public AdminPlatformController(PlatformProperties platformProperties) {
    this.platformProperties = platformProperties;
  }

  @GetMapping
  public PlatformInfoDto info() {
    boolean stub = platformProperties.getProvisioning().isUseStubs();
    return new PlatformInfoDto(
        stub,
        stub
            ? "Provisioning runs in stub mode — no real GCP DNS, Terraform, or GitHub dispatch"
            : "Production provisioning — infra workflows own DNS and tenant resources");
  }

  public record PlatformInfoDto(boolean stubProvisioning, String message) {}
}
