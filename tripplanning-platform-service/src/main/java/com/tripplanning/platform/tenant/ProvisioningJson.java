package com.tripplanning.platform.tenant;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProvisioningJson {

  private final ObjectMapper objectMapper;

  public List<TenantDtos.ProvisioningStepDto> readSteps(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Invalid provisioning_steps_json", e);
    }
  }

  public String writeSteps(List<TenantDtos.ProvisioningStepDto> steps) {
    try {
      return objectMapper.writeValueAsString(steps);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot serialize provisioning steps", e);
    }
  }

  public List<String> readProviders(String json) {
    if (json == null || json.isBlank()) {
      return List.of("password");
    }
    try {
      return objectMapper.readValue(json, new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      return List.of("password");
    }
  }

  public String writeProviders(List<String> providers) {
    try {
      return objectMapper.writeValueAsString(providers);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot serialize auth providers", e);
    }
  }
}
