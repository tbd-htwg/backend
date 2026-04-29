package com.tripplanning.auth;

import java.io.IOException;
import java.security.GeneralSecurityException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.tripplanning.user.UserEntity;
import com.tripplanning.user.UserRepository;

@RestController
@RequestMapping("/api/v2/auth")
public class AuthController {

  private final GoogleCredentialVerifier googleCredentialVerifier;
  private final GoogleUserProvisioningService provisioningService;
  private final AppJwtService appJwtService;
  private final UserRepository userRepository;

  public AuthController(
      GoogleCredentialVerifier googleCredentialVerifier,
      GoogleUserProvisioningService provisioningService,
      AppJwtService appJwtService,
      UserRepository userRepository) {
    this.googleCredentialVerifier = googleCredentialVerifier;
    this.provisioningService = provisioningService;
    this.appJwtService = appJwtService;
    this.userRepository = userRepository;
  }

  @PostMapping("/register")
  @Transactional
  public AuthDtos.LoginResponse register(@RequestBody AuthDtos.RegisterRequest body) {
    if (body.email() == null || body.email().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required");
    }
    if (body.name() == null || body.name().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
    }
    String email = body.email().trim();
    if (userRepository.findByEmail(email).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "User with this email already exists");
    }
    String name = body.name().trim();
    if (userRepository.findByName(name).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "User with this name already exists");
    }
    UserEntity user =
        userRepository.save(
            UserEntity.builder()
                .email(email)
                .name(name)
                .imagePath(body.imageUrl() != null ? body.imageUrl().trim() : "")
                .description(body.description() != null ? body.description().trim() : "")
                .build());
    String token = appJwtService.createToken(user.getId(), user.getEmail());
    return new AuthDtos.LoginResponse("Bearer", token, UserResponseMapper.fromEntity(user));
  }

  @PostMapping("/google")
  public AuthDtos.LoginResponse google(@RequestBody AuthDtos.GoogleLoginRequest body) {
    if (body.credential() == null || body.credential().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "credential is required");
    }
    try {
      GoogleIdToken.Payload payload = googleCredentialVerifier.verify(body.credential());
      UserEntity user = provisioningService.findOrCreateFromGoogle(payload);
      String token = appJwtService.createToken(user.getId(), user.getEmail());
      return new AuthDtos.LoginResponse("Bearer", token, UserResponseMapper.fromEntity(user));
    } catch (IllegalStateException e) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), e);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
    } catch (GeneralSecurityException e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google credential", e);
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not verify Google credential", e);
    }
  }

  @GetMapping("/me")
  public AuthDtos.UserResponseDto me(@AuthenticationPrincipal Jwt jwt) {
    long id = Long.parseLong(jwt.getSubject());
    return userRepository
        .findById(id)
        .map(UserResponseMapper::fromEntity)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
  }
}
