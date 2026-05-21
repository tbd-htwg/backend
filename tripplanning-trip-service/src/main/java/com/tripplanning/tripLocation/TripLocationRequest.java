package com.tripplanning.tripLocation;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class TripLocationRequest {

    private TripLocationRequest() {}

    public record CreateTripLocationRequest(
            @NotNull Long tripId,
            @NotBlank String googlePlaceId,
            String description,
            @NotNull LocalDateTime startDate,
            @NotNull LocalDateTime endDate) {}
}
