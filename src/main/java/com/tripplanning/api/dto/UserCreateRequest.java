package com.tripplanning.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
    @Schema(example = "jane.doe@example.com")
    @Email
    @NotBlank
    String email,
    @Schema(example = "Jane Doe")
    @NotBlank
    @Size(max = 255)
    String name
) {
}

