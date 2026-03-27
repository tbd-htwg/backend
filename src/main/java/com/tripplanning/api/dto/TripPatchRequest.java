package com.tripplanning.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record TripPatchRequest(
    @Schema(example = "2")
    @Positive
    Long userId,
    @Schema(example = "Renamed trip title")
    @Size(max = 255)
    String title,
    @Schema(example = "Trondheim")
    @Size(max = 255)
    String destination,
    @Schema(example = "2026-09-10")
    LocalDate startDate,
    @Schema(example = "New subtitle")
    @Size(max = 80)
    String shortDescription,
    @Schema(example = "Only fields you send are updated.")
    String longDescription
) {
}
