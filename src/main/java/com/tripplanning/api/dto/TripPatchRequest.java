package com.tripplanning.api.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record TripPatchRequest(
    @Positive
    Long userId,
    @Size(max = 255)
    String title,
    @Size(max = 255)
    String destination,
    LocalDate startDate,
    @Size(max = 80)
    String shortDescription,
    String longDescription
) {
}
