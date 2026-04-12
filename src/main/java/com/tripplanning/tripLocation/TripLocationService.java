package com.tripplanning.tripLocation;

import com.tripplanning.api.dto.request.AddStopRequest;
import com.tripplanning.api.dto.response.StopResponse;
import com.tripplanning.location.LocationEntity;
import com.tripplanning.trip.TripEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TripLocationService {

  private final TripLocationRepository tripLocationRepository;

  @Transactional
  public StopResponse addStop(TripEntity trip, LocationEntity location, AddStopRequest request) {
    TripLocationEntity stop =
        new TripLocationEntity(trip, location, request.imageUrl(), request.description());

    TripLocationEntity saved = tripLocationRepository.save(stop);
    return mapToResponse(saved);
  }

  @Transactional(readOnly = true)
  public List<StopResponse> getStopsByTrip(long tripId) {
    return tripLocationRepository.findByTrip_TripId(tripId).stream().map(this::mapToResponse).toList();
  }

  private StopResponse mapToResponse(TripLocationEntity entity) {
    return new StopResponse(
        entity.getId(),
        entity.getLocation().getName(),
        entity.getDescription(),
        entity.getImageUrl());
  }
}
