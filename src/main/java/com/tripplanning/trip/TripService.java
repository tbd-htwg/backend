package com.tripplanning.trip;

import com.tripplanning.api.dto.request.TripCreateRequest;
import com.tripplanning.api.dto.request.TripPatchRequest;
import com.tripplanning.api.dto.request.TripPutRequest;
import com.tripplanning.api.dto.response.TripDetailsResponse;
import com.tripplanning.api.dto.response.TripListItemResponse;
import com.tripplanning.api.exception.InvalidInputException;
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

  @Transactional
  public TripDetailsResponse replaceTrip(long id, TripPutRequest request) {
    TripEntity trip = requireTripById(id);
    UserEntity user = userService.requireUserById(request.userId());

    trip.setUser(user);
    trip.setTitle(request.title());
    trip.setDestination(request.destination());
    trip.setStartDate(request.startDate());
    trip.setShortDescription(request.shortDescription());
    trip.setLongDescription(request.longDescription());

    return mapTripDetails(trip);
  }

  @Transactional
  public TripDetailsResponse patchTrip(long id, TripPatchRequest request) {
    TripEntity trip = requireTripById(id);
    boolean hasChanges = false;

    if (request.userId() != null) {
      trip.setUser(userService.requireUserById(request.userId()));
      hasChanges = true;
    }
    if (request.title() != null) {
      trip.setTitle(request.title());
      hasChanges = true;
    }
    if (request.destination() != null) {
      trip.setDestination(request.destination());
      hasChanges = true;
    }
    if (request.startDate() != null) {
      trip.setStartDate(request.startDate());
      hasChanges = true;
    }
    if (request.shortDescription() != null) {
      trip.setShortDescription(request.shortDescription());
      hasChanges = true;
    }
    if (request.longDescription() != null) {
      trip.setLongDescription(request.longDescription());
      hasChanges = true;
    }

    if (!hasChanges) {
      throw new InvalidInputException("No updatable fields provided.", List.of("request"));
    }

    return mapTripDetails(trip);
  }

  @Transactional
  public void deleteTrip(long id) {
    TripEntity trip = requireTripById(id);
    tripRepository.delete(trip);
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
    return mapTripDetails(requireTripById(id));
  }

  private TripEntity requireTripById(long id) {
    return tripRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Trip does not exist.", java.util.List.of("id")));
  }

  private TripDetailsResponse mapTripDetails(TripEntity trip) {
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

