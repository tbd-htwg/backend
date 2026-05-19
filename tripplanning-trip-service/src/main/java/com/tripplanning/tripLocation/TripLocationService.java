package com.tripplanning.tripLocation;

import org.springframework.stereotype.Service;

import com.tripplanning.external.ExternalInfoClient;
import com.tripplanning.external.ExternalInfoDtos.PlaceDetailsResult;
import com.tripplanning.external.ExternalInfoDtos.TripExternalInfo;
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
    private final ExternalInfoClient externalInfoClient;
    private final TripCacheEvictor tripCacheEvictor;

    @Transactional
    public TripLocationCreatedResponse addStop(TripLocationRequest.CreateTripLocationRequest request) {
        TripEntity trip = tripRepository.findById(request.tripId()).orElseThrow();
        
        PlaceDetailsResult geo = externalInfoClient.fetchPlaceDetails(request.googlePlaceId()).block();
        if (geo == null) {
            throw new RuntimeException("Place could not be found in Google.");
        }

        TripLocationEntity stop = TripLocationEntity.builder()
                .trip(trip)
                .googlePlaceId(request.googlePlaceId())
                .cityName(geo.cityName()) 
                .description(request.description())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .build();

        TripLocationEntity saved = tripLocationRepository.save(stop);
        tripCacheEvictor.evictForTripChange(request.tripId());
        
        return TripLocationCreatedResponse.from(saved);
    }

    public TripExternalInfo getExternalDetails(Long tripLocationId) {
        TripLocationEntity stop = tripLocationRepository
                .findById(tripLocationId)
                .orElseThrow(() -> new RuntimeException("Stop not found"));
                
        // Nutzt jetzt die googlePlaceId für den Multicast-Aufruf (Wetter, Viator, AA)
        return externalInfoClient.fetchExternalDetailsForLocation(stop.getGooglePlaceId()).block();
    }
}