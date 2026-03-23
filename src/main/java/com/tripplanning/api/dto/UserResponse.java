package com.tripplanning.api.dto;

public record UserResponse(
    Long id,
    String email,
    String name
) {
}

