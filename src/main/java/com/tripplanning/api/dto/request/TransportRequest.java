package com.tripplanning.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record TransportRequest(
    @Schema(example = "Flug") 
    @NotBlank 
    String type
) {}