package com.tripplanning.api.dto;

import java.time.LocalDate;

public record TripDetailsResponse(
    Long id,
    String title,
    String destination,
    LocalDate startDate,
    String shortDescription,
    String longDescription
) {
}

