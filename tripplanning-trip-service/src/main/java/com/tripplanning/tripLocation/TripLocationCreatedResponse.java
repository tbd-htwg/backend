package com.tripplanning.tripLocation;

import java.time.LocalDateTime;

public record TripLocationCreatedResponse(
        Long id,
        Long tripId,
        String googlePlaceId,
        String cityName,
        String description,
        LocalDateTime startDate,
        LocalDateTime endDate) {

    public static TripLocationCreatedResponse from(TripLocationEntity stop) {
        return new TripLocationCreatedResponse(
                stop.getId(),
                stop.getTrip().getId(),
                stop.getGooglePlaceId(),
                stop.getCityName(),
                stop.getDescription(),
                stop.getStartDate(),
                stop.getEndDate());
    }
}
