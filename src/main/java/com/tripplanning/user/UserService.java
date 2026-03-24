package com.tripplanning.user;

import com.tripplanning.api.dto.UserCreateRequest;
import com.tripplanning.api.dto.UserDetailsResponse;
import com.tripplanning.api.dto.UserPatchRequest;
import com.tripplanning.api.dto.UserPutRequest;
import com.tripplanning.api.dto.UserResponse;
import com.tripplanning.api.exception.EmailAlreadyExistsException;
import com.tripplanning.api.exception.InvalidInputException;
import com.tripplanning.api.exception.ResourceNotFoundException;
import com.tripplanning.api.dto.TripListItemResponse;
import com.tripplanning.trip.TripEntity;
import com.tripplanning.trip.TripRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
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
    ensureEmailIsUnique(request.email(), null);

    UserEntity created = userRepository.save(new UserEntity(request.email(), request.name()));
    return new UserResponse(created.getId(), created.getEmail(), created.getName());
  }

  @Transactional
  public UserResponse replaceUser(Long id, UserPutRequest request) {
    UserEntity user = requireExistingUserForCrud(id);
    ensureEmailIsUnique(request.email(), user.getId());

    user.setEmail(request.email());
    user.setName(request.name());
    return mapUser(user);
  }

  @Transactional
  public UserResponse patchUser(Long id, UserPatchRequest request) {
    UserEntity user = requireExistingUserForCrud(id);
    boolean hasChanges = false;

    if (request.email() != null) {
      ensureEmailIsUnique(request.email(), user.getId());
      user.setEmail(request.email());
      hasChanges = true;
    }

    if (request.name() != null) {
      user.setName(request.name());
      hasChanges = true;
    }

    if (!hasChanges) {
      throw new InvalidInputException("No updatable fields provided.", List.of("request"));
    }

    return mapUser(user);
  }

  @Transactional
  public void deleteUser(Long id) {
    UserEntity user = requireExistingUserForCrud(id);
    tripRepository.deleteByUserId(user.getId());
    userRepository.delete(user);
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

  private void ensureEmailIsUnique(String email, Long currentUserId) {
    Optional<UserEntity> existing = userRepository.findByEmail(email);
    if (existing.isPresent() && !Objects.equals(existing.get().getId(), currentUserId)) {
      throw new EmailAlreadyExistsException("Email already exists.", List.of("email"));
    }
  }

  private UserEntity requireExistingUserForCrud(Long id) {
    if (id == null) {
      throw new InvalidInputException("Missing required id.", List.of("id"));
    }

    return userRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("User does not exist.", List.of("id")));
  }

  private UserResponse mapUser(UserEntity user) {
    return new UserResponse(user.getId(), user.getEmail(), user.getName());
  }
}

