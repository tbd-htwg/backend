package com.tripplanning.platform.tenant;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class TenantResourceConfigService {

  private static final int MIN_REPLICAS = 1;
  private static final int MAX_FIXED_REPLICAS = 4;
  private static final int MAX_HPA_REPLICAS = 6;
  private static final int MAX_TOTAL_HPA_REPLICAS = 15;

  private final ObjectMapper objectMapper;

  public TenantResourceConfigService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public TenantDtos.TenantResourceConfigDto read(String json) {
    if (json == null || json.isBlank()) {
      return defaults();
    }
    try {
      JsonNode node = objectMapper.readTree(json);
      boolean autoscalingEnabled = node.path("autoscalingEnabled").asBoolean(true);
      return normalize(
          new TenantDtos.TenantResourceConfigDto(
              autoscalingEnabled,
              readService(node.get("trip"), "trip", autoscalingEnabled),
              readService(node.get("social"), "social", autoscalingEnabled),
              readService(node.get("externalInfo"), "externalInfo", autoscalingEnabled),
              readService(node.get("customfield"), "customfield", autoscalingEnabled)));
    } catch (JsonProcessingException e) {
      return defaults();
    }
  }

  public String write(TenantDtos.TenantResourceConfigDto request) {
    try {
      return objectMapper.writeValueAsString(normalize(request));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize tenant resource config", e);
    }
  }

  public Map<String, Object> toWorkflowPayload(TenantDtos.TenantResourceConfigDto request) {
    TenantDtos.TenantResourceConfigDto config = normalize(request);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("autoscalingEnabled", config.autoscalingEnabled());
    Map<String, Object> services = new LinkedHashMap<>();
    services.put("trip", servicePayload(config.trip(), config.autoscalingEnabled(), "trip"));
    services.put("social", servicePayload(config.social(), config.autoscalingEnabled(), "social"));
    services.put(
        "externalInfo",
        servicePayload(config.externalInfo(), config.autoscalingEnabled(), "externalInfo"));
    services.put(
        "customfield",
        servicePayload(config.customfield(), config.autoscalingEnabled(), "customfield"));
    payload.put("services", services);
    return payload;
  }

  private TenantDtos.TenantServiceResourceDto readService(
      JsonNode node, String serviceName, boolean autoscalingEnabled) {
    if (node == null || node.isNull()) {
      return defaultService(serviceName);
    }
    try {
      return normalizeService(
          objectMapper.treeToValue(node, TenantDtos.TenantServiceResourceDto.class),
          serviceName,
          autoscalingEnabled);
    } catch (JsonProcessingException e) {
      return defaultService(serviceName);
    }
  }

  private Map<String, Object> servicePayload(
      TenantDtos.TenantServiceResourceDto service, boolean autoscalingEnabled, String serviceName) {
    ResourcePreset preset = ResourcePreset.forService(serviceName, service.size());
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("replicas", autoscalingEnabled ? service.minReplicas() : service.replicas());
    payload.put(
        "resources",
        Map.of(
            "requests",
            Map.of("cpu", preset.requestCpu(), "memory", preset.requestMemory()),
            "limits",
            Map.of("cpu", preset.limitCpu(), "memory", preset.limitMemory())));
    if (autoscalingEnabled) {
      payload.put("minReplicas", service.minReplicas());
      payload.put("maxReplicas", service.maxReplicas());
    }
    return payload;
  }

  private TenantDtos.TenantResourceConfigDto normalize(TenantDtos.TenantResourceConfigDto request) {
    TenantDtos.TenantResourceConfigDto source = request == null ? defaults() : request;
    TenantDtos.TenantServiceResourceDto trip =
        normalizeService(source.trip(), "trip", source.autoscalingEnabled());
    TenantDtos.TenantServiceResourceDto social =
        normalizeService(source.social(), "social", source.autoscalingEnabled());
    TenantDtos.TenantServiceResourceDto externalInfo =
        normalizeService(source.externalInfo(), "externalInfo", source.autoscalingEnabled());
    TenantDtos.TenantServiceResourceDto customfield =
        normalizeService(source.customfield(), "customfield", source.autoscalingEnabled());
    if (source.autoscalingEnabled()
        && trip.maxReplicas()
                + social.maxReplicas()
                + externalInfo.maxReplicas()
                + customfield.maxReplicas()
            > MAX_TOTAL_HPA_REPLICAS) {
      throw new IllegalArgumentException(
          "Total HPA max replicas must not exceed " + MAX_TOTAL_HPA_REPLICAS);
    }
    return new TenantDtos.TenantResourceConfigDto(
        source.autoscalingEnabled(), trip, social, externalInfo, customfield);
  }

  private TenantDtos.TenantServiceResourceDto normalizeService(
      TenantDtos.TenantServiceResourceDto service, String serviceName, boolean autoscalingEnabled) {
    TenantDtos.TenantServiceResourceDto fallback = defaultService(serviceName);
    String size =
        service == null || service.size() == null
            ? fallback.size()
            : service.size().trim().toUpperCase(Locale.ROOT);
    ResourcePreset.forService(serviceName, size);
    int replicas =
        bounded(
            service == null ? null : service.replicas(),
            fallback.replicas(),
            MIN_REPLICAS,
            MAX_FIXED_REPLICAS);
    int minReplicas =
        bounded(
            service == null ? null : service.minReplicas(),
            fallback.minReplicas(),
            MIN_REPLICAS,
            MAX_HPA_REPLICAS);
    int maxReplicas =
        bounded(
            service == null ? null : service.maxReplicas(),
            fallback.maxReplicas(),
            MIN_REPLICAS,
            MAX_HPA_REPLICAS);
    if (autoscalingEnabled && minReplicas > maxReplicas) {
      throw new IllegalArgumentException(
          serviceName + " min replicas must be less than or equal to max replicas");
    }
    return new TenantDtos.TenantServiceResourceDto(size, replicas, minReplicas, maxReplicas);
  }

  private static int bounded(Integer value, int fallback, int min, int max) {
    int actual = value == null ? fallback : value;
    if (actual < min || actual > max) {
      throw new IllegalArgumentException("Replica count must be between " + min + " and " + max);
    }
    return actual;
  }

  public TenantDtos.TenantResourceConfigDto defaults() {
    return new TenantDtos.TenantResourceConfigDto(
        true,
        defaultService("trip"),
        defaultService("social"),
        defaultService("externalInfo"),
        defaultService("customfield"));
  }

  private static TenantDtos.TenantServiceResourceDto defaultService(String serviceName) {
    return switch (serviceName) {
      case "trip" -> new TenantDtos.TenantServiceResourceDto("SMALL", 1, 1, 4);
      case "social" -> new TenantDtos.TenantServiceResourceDto("SMALL", 1, 1, 3);
      case "externalInfo" -> new TenantDtos.TenantServiceResourceDto("SMALL", 1, 1, 3);
      case "customfield" -> new TenantDtos.TenantServiceResourceDto("SMALL", 1, 1, 3);
      default -> throw new IllegalArgumentException("Unsupported service: " + serviceName);
    };
  }

  private record ResourcePreset(
      String requestCpu, String requestMemory, String limitCpu, String limitMemory) {
    static ResourcePreset forService(String service, String size) {
      String key = size == null ? "SMALL" : size.toUpperCase(Locale.ROOT);
      return switch (service + ":" + key) {
        case "trip:SMALL" -> new ResourcePreset("100m", "512Mi", "1000m", "1Gi");
        case "trip:MEDIUM" -> new ResourcePreset("600m", "768Mi", "1500m", "1536Mi");
        case "trip:LARGE" -> new ResourcePreset("1000m", "1Gi", "2000m", "2Gi");
        case "social:SMALL" -> new ResourcePreset("50m", "384Mi", "750m", "768Mi");
        case "social:MEDIUM" -> new ResourcePreset("400m", "512Mi", "1000m", "1Gi");
        case "social:LARGE" -> new ResourcePreset("750m", "768Mi", "1500m", "1536Mi");
        case "externalInfo:SMALL" -> new ResourcePreset("50m", "256Mi", "500m", "512Mi");
        case "externalInfo:MEDIUM" -> new ResourcePreset("300m", "384Mi", "750m", "768Mi");
        case "externalInfo:LARGE" -> new ResourcePreset("500m", "512Mi", "1000m", "1Gi");
        case "customfield:SMALL" -> new ResourcePreset("50m", "256Mi", "500m", "512Mi");
        case "customfield:MEDIUM" -> new ResourcePreset("300m", "384Mi", "750m", "768Mi");
        case "customfield:LARGE" -> new ResourcePreset("500m", "512Mi", "1000m", "1Gi");
        default -> throw new IllegalArgumentException("Unsupported resource size " + size + " for " + service);
      };
    }
  }
}
