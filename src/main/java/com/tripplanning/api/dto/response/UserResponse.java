package com.tripplanning.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record UserResponse(
    @Schema(example = "1")
    Long id,
    @Schema(example = "jane.doe@example.com")
    String email,
    @Schema(example = "Jane Doe")
    String name,
    @Size(max = 500)
    @Schema(example = "https://mein-cloud-speicher.de/foto123.jpg")
    String imageUrl,
    @Schema(example = "Travel enthusiast, 25 years old.")
    String description
) {
}

