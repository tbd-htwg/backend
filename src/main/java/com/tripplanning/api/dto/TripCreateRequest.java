package com.tripplanning.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record TripCreateRequest(
    @Schema(example = "1")
    @NotNull
    @Positive
    Long userId,
    @Schema(example = "Family trip to Norway")
    @NotBlank
    @Size(max = 255)
    String title,
    @Schema(example = "Oslo")
    @NotBlank
    @Size(max = 255)
    String destination,
    @Schema(example = "2026-07-15")
    @NotNull
    LocalDate startDate,
    @Schema(example = "Summer holiday with the kids")
    @NotBlank
    @Size(max = 80)
    String shortDescription,
    @Schema(example = "Full itinerary, budget notes, and packing list.")
    @NotBlank
    String longDescription
) {
}

