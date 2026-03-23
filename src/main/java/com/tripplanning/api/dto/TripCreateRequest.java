package com.tripplanning.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record TripCreateRequest(
    @NotNull
    @Positive
    Long userId,
    @NotBlank
    @Size(max = 255)
    String title,
    @NotBlank
    @Size(max = 255)
    String destination,
    @NotNull
    LocalDate startDate,
    @NotBlank
    @Size(max = 80)
    String shortDescription,
    @NotBlank
    String longDescription
) {
}

