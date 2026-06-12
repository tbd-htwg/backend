package com.tripplanning.internal;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.tripplanning.auth.AuthDtos;
import com.tripplanning.auth.GoogleUserProvisioningService;
import com.tripplanning.auth.UserResponseMapper;
import com.tripplanning.user.UserRepository;

@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

  private final GoogleUserProvisioningService provisioningService;
  private final UserRepository userRepository;
  private final UserResponseMapper userResponseMapper;

  public InternalUserController(
      GoogleUserProvisioningService provisioningService,
      UserRepository userRepository,
      UserResponseMapper userResponseMapper) {
    this.provisioningService = provisioningService;
    this.userRepository = userRepository;
    this.userResponseMapper = userResponseMapper;
  }

  @PostMapping("/provision-identity")
  public AuthDtos.UserResponseDto provisionIdentity(
      @RequestBody InternalUserDtos.IdentityProvisionRequest body) {
    try {
      return userResponseMapper.fromEntity(
          provisioningService.findOrCreateFromIdentity(
              body.sub(), body.email(), body.name(), body.picture()));
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
    }
  }

  @PostMapping("/provision-dev")
  public AuthDtos.UserResponseDto provisionDev(@RequestBody InternalUserDtos.DevProvisionRequest body) {
    try {
      return userResponseMapper.fromEntity(
          provisioningService.findOrCreateDevUser(body.email(), body.name()));
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
    }
  }

  @GetMapping("/{id}")
  public AuthDtos.UserResponseDto getUser(@PathVariable long id) {
    return userRepository
        .findById(id)
        .map(userResponseMapper::fromEntity)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
  }
}
