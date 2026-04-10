package com.tripplanning.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UserPatchRequest(
    @Schema(example = "jane.new@example.com")
    @Email
    String email,
    @Schema(example = "Jane D.")
    @Size(max = 255)
    String name,
    @Size(max = 500)
    @Schema(example = "https://mein-cloud-speicher.de/foto123.jpg")
    String imageUrl,
    @Schema(example = "Travel enthusiast, 25 years old.")
    String description
) {
}
