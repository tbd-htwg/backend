package com.tripplanning.user;

import com.tripplanning.api.dto.UserCreateRequest;
import com.tripplanning.api.dto.UserResponse;
import com.tripplanning.api.exception.EmailAlreadyExistsException;
import com.tripplanning.api.exception.InvalidInputException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserService {
  private final UserRepository userRepository;

  public UserService(UserRepository userRepository) {
    this.userRepository = userRepository;
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
}

