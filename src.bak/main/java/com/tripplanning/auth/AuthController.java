package com.tripplanning.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.tripplanning.user.UserEntity;
import com.tripplanning.user.UserRepository;

@RestController
@RequestMapping("/api/v2/auth")
public class AuthController {

  private final FirebaseCredentialVerifier firebaseCredentialVerifier;
  private final GoogleUserProvisioningService provisioningService;
  private final AppJwtService appJwtService;
  private final UserRepository userRepository;
  private final UserResponseMapper userResponseMapper;

  public AuthController(
      FirebaseCredentialVerifier firebaseCredentialVerifier,
      GoogleUserProvisioningService provisioningService,
      AppJwtService appJwtService,
      UserRepository userRepository,
      UserResponseMapper userResponseMapper) {
    this.firebaseCredentialVerifier = firebaseCredentialVerifier;
    this.provisioningService = provisioningService;
    this.appJwtService = appJwtService;
    this.userRepository = userRepository;
    this.userResponseMapper = userResponseMapper;
  }

  @PostMapping("/google")
  public AuthDtos.LoginResponse google(@RequestBody AuthDtos.GoogleLoginRequest body) {
    if (body.credential() == null || body.credential().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "credential is required");
    }
    try {
      Jwt payload = firebaseCredentialVerifier.verify(body.credential());
      UserEntity user = provisioningService.findOrCreateFromGoogle(payload);
      String token = appJwtService.createToken(user.getId(), user.getEmail());
      return new AuthDtos.LoginResponse("Bearer", token, userResponseMapper.fromEntity(user));
    } catch (IllegalStateException e) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), e);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
    } catch (JwtException e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid identity credential", e);
    }
  }

  @GetMapping("/me")
  public AuthDtos.UserResponseDto me(@AuthenticationPrincipal Jwt jwt) {
    long id = Long.parseLong(jwt.getSubject());
    return userRepository
        .findById(id)
        .map(userResponseMapper::fromEntity)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
  }
}
