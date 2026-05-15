package com.tripplanning.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.tripplanning.user.UserEntity;
import com.tripplanning.user.UserRepository;

/**
 * Local profile only: obtain an application JWT without Google. Never registered in production
 * where the {@code local} Spring profile is inactive.
 */
@Profile("local")
@RestController
@RequestMapping("/api/v2/auth")
public class DevAuthController {

  private final UserRepository userRepository;
  private final AppJwtService appJwtService;
  private final UserResponseMapper userResponseMapper;

  public DevAuthController(
      UserRepository userRepository,
      AppJwtService appJwtService,
      UserResponseMapper userResponseMapper) {
    this.userRepository = userRepository;
    this.appJwtService = appJwtService;
    this.userResponseMapper = userResponseMapper;
  }

  @PostMapping("/dev-login")
  @Transactional
  public AuthDtos.LoginResponse devLogin(@RequestBody AuthDtos.DevLoginRequest body) {
    if (body.email() == null || body.email().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required");
    }
    String email = body.email().trim();
    UserEntity user =
        userRepository
            .findByEmail(email)
            .orElseGet(
                () -> {
                  String name =
                      body.name() != null && !body.name().isBlank()
                          ? body.name().trim()
                          : deriveNameFromEmail(email);
                  return userRepository.save(
                      UserEntity.builder()
                          .email(email)
                          .name(uniqueName(name))
                          .imagePath("")
                          .description("")
                          .build());
                });

    String token = appJwtService.createToken(user.getId(), user.getEmail());
    return new AuthDtos.LoginResponse("Bearer", token, userResponseMapper.fromEntity(user));
  }

  private String uniqueName(String base) {
    String candidate = truncate(base, 255);
    if (userRepository.findByName(candidate).isEmpty()) {
      return candidate;
    }
    for (int i = 0; i < 50; i++) {
      String c = truncate(base + "_dev" + i, 255);
      if (userRepository.findByName(c).isEmpty()) {
        return c;
      }
    }
    throw new IllegalStateException("Could not allocate unique name");
  }

  private static String deriveNameFromEmail(String email) {
    int at = email.indexOf('@');
    return at > 0 ? email.substring(0, at) : email;
  }

  private static String truncate(String s, int max) {
    if (s.length() <= max) {
      return s;
    }
    return s.substring(0, max);
  }
}
