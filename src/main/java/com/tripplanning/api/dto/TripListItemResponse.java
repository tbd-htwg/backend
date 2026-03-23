package com.tripplanning.api.dto;

import java.time.LocalDate;

public record TripListItemResponse(
    Long id,
    String title,
    LocalDate startDate
) {
}

