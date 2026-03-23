package com.tripplanning.trip;

import com.tripplanning.api.dto.TripCreateRequest;
import com.tripplanning.api.dto.TripDetailsResponse;
import com.tripplanning.api.dto.TripListItemResponse;
import com.tripplanning.api.exception.ResourceNotFoundException;
import com.tripplanning.user.UserEntity;
import com.tripplanning.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TripService {

  private final TripRepository tripRepository;
  private final UserService userService;

  public TripService(TripRepository tripRepository, UserService userService) {
    this.tripRepository = tripRepository;
    this.userService = userService;
  }

  @Transactional
  public TripDetailsResponse create(TripCreateRequest request) {
    UserEntity user = userService.requireUserById(request.userId());
    return createForUser(request, user);
  }

  // Separate method keeps the JSON parsing/validation concerns away from entity creation.
  @Transactional
  private TripDetailsResponse createForUser(TripCreateRequest request, UserEntity user) {
    TripEntity entity = new TripEntity(
        user,
        request.title(),
        request.destination(),
        request.startDate(),
        request.shortDescription(),
        request.longDescription()
    );

    TripEntity saved = tripRepository.save(entity);
    return new TripDetailsResponse(
        saved.getId(),
        saved.getTitle(),
        saved.getDestination(),
        saved.getStartDate(),
        saved.getShortDescription(),
        saved.getLongDescription()
    );
  }

  public List<TripListItemResponse> listTrips() {
    return tripRepository
        .findAll()
        .stream()
        .map(t -> new TripListItemResponse(t.getId(), t.getTitle(), t.getStartDate()))
        .toList();
  }

  public TripDetailsResponse getTrip(long id) {
    TripEntity trip = tripRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Trip does not exist.", java.util.List.of("id")));

    return new TripDetailsResponse(
        trip.getId(),
        trip.getTitle(),
        trip.getDestination(),
        trip.getStartDate(),
        trip.getShortDescription(),
        trip.getLongDescription()
    );
  }
}

