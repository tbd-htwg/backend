package com.tripplanning.tripLocation;

import org.springframework.stereotype.Service;

import com.tripplanning.external.ExternalInfoClient;
import com.tripplanning.external.ExternalInfoDtos.GeocodingResult;
import com.tripplanning.external.ExternalInfoDtos.TripExternalInfo;
import com.tripplanning.location.LocationEntity;
import com.tripplanning.location.LocationService;
import com.tripplanning.trip.TripEntity;
import com.tripplanning.trip.TripRepository;
import com.tripplanning.trip.read.TripCacheEvictor;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TripLocationService {

    private final TripLocationRepository tripLocationRepository;
    private final LocationService locationService;
    private final TripRepository tripRepository;
    private final ExternalInfoClient externalInfoClient;
    private final TripCacheEvictor tripCacheEvictor;

    @Transactional
    public TripLocationCreatedResponse addStop(TripLocationRequest.CreateTripLocationRequest request) {
        TripEntity trip = tripRepository.findById(request.tripId()).orElseThrow();
        GeocodingResult resolved = null;
        if (request.countryCode() != null
                && !request.countryCode().isBlank()
                && request.latitude() != null
                && request.longitude() != null) {
            resolved = new GeocodingResult(
                    request.city(),
                    request.formattedAddress() != null ? request.formattedAddress() : request.city(),
                    request.latitude(),
                    request.longitude(),
                    request.countryCode().trim().toUpperCase());
        }
        LocationEntity location = locationService.getOrCreateLocation(request.city(), resolved);

        TripLocationEntity stop = new TripLocationEntity();
        stop.setTrip(trip);
        stop.setLocation(location);
        stop.setDescription(request.description());
        stop.setStartDate(request.startDate());
        stop.setEndDate(request.endDate());

        TripLocationEntity saved = tripLocationRepository.save(stop);
        tripCacheEvictor.evictForTripChange(request.tripId());
        return TripLocationCreatedResponse.from(saved);
    }

    public TripExternalInfo getExternalDetails(Long tripLocationId) {
        TripLocationEntity stop = tripLocationRepository
                .findById(tripLocationId)
                .orElseThrow(() -> new RuntimeException("Stop not found"));
        return externalInfoClient.fetchExternalDetailsForLocation(stop.getLocation()).block();
    }
}
