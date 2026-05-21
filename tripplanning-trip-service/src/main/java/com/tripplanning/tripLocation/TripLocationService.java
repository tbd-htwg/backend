package com.tripplanning.tripLocation;

import org.springframework.stereotype.Service;

import com.tripplanning.external.ExternalInfoDtos.PlaceDetailsResult;
import com.tripplanning.external.PlaceEnrichmentHelper;
import com.tripplanning.place.PlaceService;
import com.tripplanning.trip.TripEntity;
import com.tripplanning.trip.TripRepository;
import com.tripplanning.trip.read.TripCacheEvictor;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TripLocationService {

    private final TripLocationRepository tripLocationRepository;
    private final TripRepository tripRepository;
    private final PlaceEnrichmentHelper placeEnrichmentHelper;
    private final PlaceService placeService;
    private final TripCacheEvictor tripCacheEvictor;

    @Transactional
    public TripLocationCreatedResponse addStop(TripLocationRequest.CreateTripLocationRequest request) {
        TripEntity trip = tripRepository.findById(request.tripId()).orElseThrow();
        
        PlaceDetailsResult geo = placeEnrichmentHelper.requirePlaceDetails(request.googlePlaceId());

        TripLocationEntity stop = TripLocationEntity.builder()
                .trip(trip)
                .googlePlaceId(request.googlePlaceId())
                .placeName(geo.placeName())
                .cityName(geo.cityName())
                .description(request.description())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .build();

        TripLocationEntity saved = tripLocationRepository.save(stop);
        tripCacheEvictor.evictForTripChange(request.tripId());
        
        return TripLocationCreatedResponse.from(saved);
    }

    public PlaceDetailsResult getExternalDetails(Long tripLocationId) {
        TripLocationEntity stop =
                tripLocationRepository
                        .findById(tripLocationId)
                        .orElseThrow(() -> new RuntimeException("Stop not found"));

        return placeService
                .findPlaceForRead(stop.getGooglePlaceId())
                .map(placeService::toDetailsResult)
                .orElseGet(() -> placeEnrichmentHelper.requirePlaceDetails(stop.getGooglePlaceId()));
    }
}