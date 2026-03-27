package com.tripplanning.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UserPatchRequest(
    @Schema(example = "jane.new@example.com")
    @Email
    String email,
    @Schema(example = "Jane D.")
    @Size(max = 255)
    String name
) {
}
