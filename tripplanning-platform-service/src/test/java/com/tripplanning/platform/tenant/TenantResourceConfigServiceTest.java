package com.tripplanning.platform.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class TenantResourceConfigServiceTest {

  private final TenantResourceConfigService service =
      new TenantResourceConfigService(new ObjectMapper());

  @Test
  void buildsWorkflowPayloadWithBoundedAutoscalingValues() {
    TenantDtos.TenantResourceConfigDto config =
        new TenantDtos.TenantResourceConfigDto(
            true,
            new TenantDtos.TenantServiceResourceDto("MEDIUM", 1, 2, 4),
            new TenantDtos.TenantServiceResourceDto("SMALL", 1, 1, 3),
            new TenantDtos.TenantServiceResourceDto("LARGE", 1, 1, 3));

    Map<String, Object> payload = service.toWorkflowPayload(config);

    assertThat(payload).containsEntry("autoscalingEnabled", true);
    @SuppressWarnings("unchecked")
    Map<String, Object> services = (Map<String, Object>) payload.get("services");
    @SuppressWarnings("unchecked")
    Map<String, Object> trip = (Map<String, Object>) services.get("trip");
    assertThat(trip).containsEntry("replicas", 2);
    assertThat(trip).containsEntry("minReplicas", 2);
    assertThat(trip).containsEntry("maxReplicas", 4);
    assertThat(trip.toString()).contains("600m", "768Mi", "1500m", "1536Mi");
  }

  @Test
  void rejectsReplicaRangesOutsideGuardrails() {
    TenantDtos.TenantResourceConfigDto config =
        new TenantDtos.TenantResourceConfigDto(
            true,
            new TenantDtos.TenantServiceResourceDto("LARGE", 1, 6, 6),
            new TenantDtos.TenantServiceResourceDto("LARGE", 1, 6, 6),
            new TenantDtos.TenantServiceResourceDto("LARGE", 1, 1, 6));

    assertThatThrownBy(() -> service.write(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Total HPA max replicas");
  }

  @Test
  void rejectsUnsupportedResourceSizes() {
    TenantDtos.TenantResourceConfigDto config =
        new TenantDtos.TenantResourceConfigDto(
            false,
            new TenantDtos.TenantServiceResourceDto("XL", 1, 1, 3),
            null,
            null);

    assertThatThrownBy(() -> service.write(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported resource size");
  }
}
