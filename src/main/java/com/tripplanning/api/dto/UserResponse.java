package com.tripplanning.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record UserResponse(
    @Schema(example = "1")
    Long id,
    @Schema(example = "jane.doe@example.com")
    String email,
    @Schema(example = "Jane Doe")
    String name
) {
}

