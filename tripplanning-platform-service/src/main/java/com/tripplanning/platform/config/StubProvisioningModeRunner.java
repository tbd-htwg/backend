package com.tripplanning.platform.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class StubProvisioningModeRunner implements ApplicationRunner {

  private final PlatformProperties platformProperties;

  @Override
  public void run(ApplicationArguments args) {
    if (platformProperties.getProvisioning().isUseStubs()) {
      log.warn(
          "Provisioning STUB mode enabled (TRIPPLANNING_PLATFORM_USE_STUBS=true) — "
              + "no real GCP DNS, Terraform, or GitHub dispatch resources will be created");
    }
  }
}
