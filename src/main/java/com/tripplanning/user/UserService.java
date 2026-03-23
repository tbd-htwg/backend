package com.tripplanning.user;

import com.tripplanning.api.dto.UserCreateRequest;
import com.tripplanning.api.dto.UserDetailsResponse;
import com.tripplanning.api.dto.UserResponse;
import com.tripplanning.api.exception.EmailAlreadyExistsException;
import com.tripplanning.api.exception.InvalidInputException;
import com.tripplanning.api.exception.ResourceNotFoundException;
import com.tripplanning.trip.TripEntity;
import com.tripplanning.trip.TripRepository;
import com.tripplanning.api.dto.TripListItemResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {
  private final UserRepository userRepository;
  private final TripRepository tripRepository;

  public UserService(UserRepository userRepository, TripRepository tripRepository) {
    this.userRepository = userRepository;
    this.tripRepository = tripRepository;
  }

  @Transactional
  public UserResponse register(UserCreateRequest request) {
    Optional<UserEntity> existing = userRepository.findByEmail(request.email());
    if (existing.isPresent()) {
      throw new EmailAlreadyExistsException(
          "Email already exists.",
          java.util.List.of("email")
      );
    }

    UserEntity created = userRepository.save(new UserEntity(request.email(), request.name()));
    return new UserResponse(created.getId(), created.getEmail(), created.getName());
  }

  @Transactional(readOnly = true)
  public UserEntity requireUserById(Long userId) {
    if (userId == null) {
      throw new InvalidInputException("Missing required userId.", java.util.List.of("userId"));
    }

    return userRepository
        .findById(userId)
        .orElseThrow(() -> new InvalidInputException(
            "Invalid userId.",
            java.util.List.of("userId")
        ));
  }

  @Transactional(readOnly = true)
  public List<UserResponse> listUsers() {
    return userRepository
        .findAll()
        .stream()
        .map(user -> new UserResponse(user.getId(), user.getEmail(), user.getName()))
        .toList();
  }

  @Transactional(readOnly = true)
  public UserDetailsResponse getUserByIdWithTrips(Long id) {
    if (id == null) {
      throw new InvalidInputException("Missing required id.", List.of("id"));
    }

    UserEntity user = userRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("User does not exist.", List.of("id")));

    List<TripListItemResponse> trips = tripRepository.findByUserId(id)
        .stream()
        .map(this::mapTripListItem)
        .toList();

    return new UserDetailsResponse(
        user.getId(),
        user.getEmail(),
        user.getName(),
        trips
    );
  }

  private TripListItemResponse mapTripListItem(TripEntity trip) {
    return new TripListItemResponse(trip.getId(), trip.getTitle(), trip.getStartDate());
  }
}

