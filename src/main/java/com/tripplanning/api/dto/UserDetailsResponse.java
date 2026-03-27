package com.tripplanning.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record UserDetailsResponse(
    @Schema(example = "1")
    Long id,
    @Schema(example = "jane.doe@example.com")
    String email,
    @Schema(example = "Jane Doe")
    String name,
    @Schema(description = "Trips owned by this user")
    List<TripListItemResponse> trips
) {
}
