package com.tripplanning.common.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/** Warns when a non-production auth shortcut is enabled on a production-like profile. */
@Component
@Slf4j
public class TestBearerMisconfigurationWarning {

  private final Environment environment;
  private final String testBearerToken;

  public TestBearerMisconfigurationWarning(
      Environment environment,
      @Value("${tripplanning.auth.test-bearer-token:}") String testBearerToken) {
    this.environment = environment;
    this.testBearerToken = testBearerToken;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void warnIfTestBearerEnabledOnK8s() {
    if (testBearerToken == null || testBearerToken.isBlank()) {
      return;
    }
    for (String profile : environment.getActiveProfiles()) {
      if ("k8s".equalsIgnoreCase(profile)) {
        log.warn(
            "TRIPPLANNING_AUTH_TEST_BEARER_TOKEN is set while profile '{}' is active — "
                + "remove this secret in production deployments",
            profile);
        return;
      }
    }
  }
}
