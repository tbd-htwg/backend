package com.tripplanning.internal;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.tripplanning.auth.AuthDtos;
import com.tripplanning.common.internal.InternalUserDto;
import com.tripplanning.auth.GoogleUserProvisioningService;
import com.tripplanning.auth.UserResponseMapper;
import com.tripplanning.user.UserEntity;
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

  @GetMapping(params = "ids")
  public InternalUserDto[] usersByIds(@RequestParam("ids") String idsParam) {
    if (idsParam == null || idsParam.isBlank()) {
      return new InternalUserDto[0];
    }
    var ids =
        java.util.Arrays.stream(idsParam.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(Long::parseLong)
            .toList();
    return userRepository.findAllById(ids).stream()
        .map(u -> new InternalUserDto(u.getId(), u.getName()))
        .toArray(InternalUserDto[]::new);
  }

  @GetMapping
  public List<InternalUserDtos.TenantUserDto> listUsers() {
    return userRepository.findAll().stream().map(this::toTenantUser).toList();
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

  @DeleteMapping("/{id}")
  public void deleteUser(@PathVariable long id) {
    if (!userRepository.existsById(id)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
    }
    userRepository.deleteById(id);
  }

  private InternalUserDtos.TenantUserDto toTenantUser(UserEntity entity) {
    return new InternalUserDtos.TenantUserDto(
        entity.getId(),
        entity.getName(),
        entity.getEmail(),
        entity.getDescription() != null ? entity.getDescription() : "");
  }
}
