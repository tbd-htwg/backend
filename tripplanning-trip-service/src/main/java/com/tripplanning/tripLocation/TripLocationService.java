package com.tripplanning.tripLocation;

import org.springframework.stereotype.Service;

import com.tripplanning.external.ExternalInfoClient;
import com.tripplanning.external.ExternalInfoDtos.TripExternalInfo;
import com.tripplanning.location.LocationEntity;
import com.tripplanning.location.LocationService;
import com.tripplanning.trip.TripEntity;
import com.tripplanning.trip.TripRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TripLocationService {

    private final TripLocationRepository tripLocationRepository;
    private final LocationService locationService;
    private final TripRepository tripRepository;
    private final ExternalInfoClient externalInfoClient;

    @Transactional
    public TripLocationEntity addStop(TripLocationRequest.CreateTripLocationRequest request) {
        TripEntity trip = tripRepository.findById(request.tripId()).orElseThrow();
        LocationEntity location = locationService.getOrCreateLocation(request.city());

        TripLocationEntity stop = new TripLocationEntity();
        stop.setTrip(trip);
        stop.setLocation(location);
        stop.setDescription(request.description());
        stop.setStartDate(request.startDate());
        stop.setEndDate(request.endDate());

        return tripLocationRepository.save(stop);
    }

    public TripExternalInfo getExternalDetails(Long tripLocationId) {
        TripLocationEntity stop = tripLocationRepository
                .findById(tripLocationId)
                .orElseThrow(() -> new RuntimeException("Stop not found"));
        return externalInfoClient.fetchExternalDetailsForLocation(stop.getLocation()).block();
    }
}
