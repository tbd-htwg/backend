package com.tripplanning.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record AccomRequest(
    @Schema(example = "Hotel Sunshine") 
    @NotBlank 
    String name,
    @Schema(example = "Hotel")
    String type,
    @Schema(example = "Musterstraße 77, Berlin") 
    String address
) {}
