package com.tripplanning.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record TripListItemResponse(
    @Schema(example = "42")
    Long id,
    @Schema(example = "Family trip to Norway")
    String title,
    @Schema(example = "2026-07-15")
    LocalDate startDate
) {
}

