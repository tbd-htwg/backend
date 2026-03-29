package com.tripplanning.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record TripDetailsResponse(
    @Schema(example = "42")
    Long id,
    @Schema(example = "Family trip to Norway")
    String title,
    @Schema(example = "Oslo")
    String destination,
    @Schema(example = "2026-07-15")
    LocalDate startDate,
    @Schema(example = "Summer holiday with the kids")
    String shortDescription,
    @Schema(example = "Full itinerary, budget notes, and packing list.")
    String longDescription
) {
}

