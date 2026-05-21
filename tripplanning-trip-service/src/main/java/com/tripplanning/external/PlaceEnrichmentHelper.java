package com.tripplanning.external;

import org.springframework.stereotype.Component;

import com.tripplanning.external.ExternalInfoDtos.PlaceDetailsResult;
import com.tripplanning.place.PlaceService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PlaceEnrichmentHelper {

    private final PlaceService placeService;

    /** Live Google enrichment and upsert into {@code google_places}. */
    public PlaceDetailsResult requirePlaceDetails(String placeId) {
        return placeService.toDetailsResult(placeService.resolvePlaceForWrite(placeId));
    }
}
