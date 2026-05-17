package com.tripplanning.tripLocation;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class TripLocationRequest {

    private TripLocationRequest() {}

    public record CreateTripLocationRequest(
            @NotNull Long tripId,
            @NotBlank String city,
            String formattedAddress,
            String countryCode,
            Double latitude,
            Double longitude,
            String description,
            @NotNull LocalDateTime startDate,
            @NotNull LocalDateTime endDate) {}
}
