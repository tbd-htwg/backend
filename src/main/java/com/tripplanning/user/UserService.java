package com.tripplanning.user;

import com.tripplanning.api.dto.request.UserCreateRequest;
import com.tripplanning.api.dto.request.UserPatchRequest;
import com.tripplanning.api.dto.request.UserPutRequest;
import com.tripplanning.api.dto.response.TripListItemResponse;
import com.tripplanning.api.dto.response.UserDetailsResponse;
import com.tripplanning.api.dto.response.UserResponse;
import com.tripplanning.api.exception.EmailAlreadyExistsException;
import com.tripplanning.api.exception.InvalidInputException;
import com.tripplanning.api.exception.ResourceNotFoundException;
import com.tripplanning.comment.CommentRepository;
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
  private final CommentRepository commentRepository;

  public UserService(UserRepository userRepository, TripRepository tripRepository, CommentRepository commentRepository) {
    this.userRepository = userRepository;
    this.tripRepository = tripRepository;
    this.commentRepository = commentRepository;
  }

  @Transactional
  public UserResponse register(UserCreateRequest request) {
    ensureEmailIsUnique(request.email(), null);

    UserEntity created =
        userRepository.save(
            new UserEntity(request.email(), request.name(), request.imageUrl(), request.description()));
    return new UserResponse(
        created.getUserId(), created.getEmail(), created.getName(), created.getImageUrl(), created.getDescription());
  }

  @Transactional
  public UserResponse replaceUser(Long id, UserPutRequest request) {
    UserEntity user = requireExistingUserForCrud(id);
    ensureEmailIsUnique(request.email(), user.getUserId());

    user.setEmail(request.email());
    user.setName(request.name());
    user.setImageUrl(request.imageUrl());
    user.setDescription(request.description());
    return mapUser(user);
  }

  @Transactional
  public UserResponse patchUser(Long id, UserPatchRequest request) {
    UserEntity user = requireExistingUserForCrud(id);
    boolean hasChanges = false;

    if (request.email() != null) {
      ensureEmailIsUnique(request.email(), user.getUserId());
      user.setEmail(request.email());
      hasChanges = true;
    }

    if (request.name() != null) {
      user.setName(request.name());
      hasChanges = true;
    }

    if (request.imageUrl() != null) {
      user.setImageUrl(request.imageUrl());
      hasChanges = true;
    }

    if (request.description() != null) {
      user.setDescription(request.description());
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
    Long userId = user.getUserId();
    commentRepository.deleteByUser_UserId(userId);
    user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User does not exist.", List.of("id")));
    user.getLikedTrips().clear();
    userRepository.delete(user);
  }

  @Transactional(readOnly = true)
  public UserEntity requireUserById(Long userId) {
    if (userId == null) {
      throw new InvalidInputException("Missing required userId.", java.util.List.of("userId"));
    }

    return userRepository
        .findById(userId)
        .orElseThrow(() -> new InvalidInputException("Invalid userId.", java.util.List.of("userId")));
  }

  @Transactional(readOnly = true)
  public List<UserResponse> listUsers() {
    return userRepository.findAll().stream()
        .map(
            user ->
                new UserResponse(
                    user.getUserId(), user.getEmail(), user.getName(), user.getImageUrl(), user.getDescription()))
        .toList();
  }

  @Transactional(readOnly = true)
  public UserDetailsResponse getUserByIdWithTrips(Long id) {
    if (id == null) {
      throw new InvalidInputException("Missing required id.", List.of("id"));
    }

    UserEntity user =
        userRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User does not exist.", List.of("id")));

    List<TripListItemResponse> ownTrips =
        tripRepository.findByUser_UserId(id).stream().map(this::mapTripListItem).toList();

    List<TripListItemResponse> likedTrips =
        tripRepository.findByLikedByUsers_UserId(id).stream().map(this::mapTripListItem).toList();

    return new UserDetailsResponse(
        user.getUserId(),
        user.getEmail(),
        user.getName(),
        user.getImageUrl(),
        user.getDescription(),
        ownTrips,
        likedTrips);
  }

  private TripListItemResponse mapTripListItem(TripEntity trip) {
    return new TripListItemResponse(trip.getTripId(), trip.getTitle(), trip.getStartDate());
  }

  private void ensureEmailIsUnique(String email, Long currentUserId) {
    Optional<UserEntity> existing = userRepository.findByEmail(email);
    if (existing.isPresent() && !Objects.equals(existing.get().getUserId(), currentUserId)) {
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
    return new UserResponse(user.getUserId(), user.getEmail(), user.getName(), user.getImageUrl(), user.getDescription());
  }
}
