package com.tripplanning.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UserPatchRequest(
    @Email
    String email,
    @Size(max = 255)
    String name
) {
}
