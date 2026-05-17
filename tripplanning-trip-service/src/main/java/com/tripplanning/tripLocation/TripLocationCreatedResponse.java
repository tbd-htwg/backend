package com.tripplanning.tripLocation;

import java.time.LocalDateTime;

public record TripLocationCreatedResponse(
        Long id,
        Long tripId,
        Long locationId,
        String locationName,
        String formattedAddress,
        String description,
        LocalDateTime startDate,
        LocalDateTime endDate) {

    public static TripLocationCreatedResponse from(TripLocationEntity stop) {
        var location = stop.getLocation();
        return new TripLocationCreatedResponse(
                stop.getId(),
                stop.getTrip().getId(),
                location.getId(),
                location.getCity(),
                location.getFormattedAddress(),
                stop.getDescription(),
                stop.getStartDate(),
                stop.getEndDate());
    }
}
