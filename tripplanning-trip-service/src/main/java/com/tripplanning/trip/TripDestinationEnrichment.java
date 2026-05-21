package com.tripplanning.trip;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.tripplanning.external.ExternalInfoDtos.PlaceDetailsResult;
import com.tripplanning.external.PlaceEnrichmentHelper;

import lombok.RequiredArgsConstructor;

/**
 * Resolves {@link TripEntity#getDestinationGooglePlaceId()} via Google Places and writes the
 * denormalized {@link TripEntity#getDestination()} label (place name) for search and display.
 * Clients must not send a free-text destination.
 */
@Component
@RequiredArgsConstructor
public class TripDestinationEnrichment {

    private final PlaceEnrichmentHelper placeEnrichmentHelper;

    public void apply(TripEntity trip) {
        String placeId = trip.getDestinationGooglePlaceId();
        if (placeId == null || placeId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "destinationGooglePlaceId is required.");
        }
        String normalizedId = placeId.trim();
        PlaceDetailsResult geo = placeEnrichmentHelper.requirePlaceDetails(normalizedId);
        trip.setDestinationGooglePlaceId(normalizedId);
        trip.setDestination(geo.placeName());
    }
}
