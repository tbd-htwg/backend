package com.tripplanning.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TenantPlatformClientTest {

  @Test
  void allowsNamingFallbackOnlyForLocalDevelopment() {
    TenantPlatformClient client = new TenantPlatformClient("http://127.0.0.1:1", "");

    TenantPlatformClient.TenantRuntime runtime = client.resolve("std2");

    assertThat(runtime.dbName()).isEqualTo("tripplanning_std_std2");
    assertThat(runtime.dbPassword()).isEmpty();
  }

  @Test
  void rejectsFallbackCredentialsWhenInternalSecretIsConfigured() {
    TenantPlatformClient client =
        new TenantPlatformClient("http://127.0.0.1:1", "production-internal-secret");

    assertThatThrownBy(() -> client.resolve("std2"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("std2");
  }
}
