package com.tripplanning.auth;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;

import com.tripplanning.user.UserEntity;
import com.tripplanning.user.UserRepository;

@Service
public class GoogleUserProvisioningService {

  private static final int MAX_NAME_SUFFIX_ATTEMPTS = 50;
  private static final int NAME_MAX_LEN = 255;

  private final UserRepository userRepository;

  public GoogleUserProvisioningService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Transactional
  public UserEntity findOrCreateFromGoogle(Jwt payload) {
    String sub = payload.getSubject();
    String email = payload.getClaimAsString("email");
    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("Identity token did not include an email");
    }
    String name = deriveDisplayName(payload, email);
    String picture = stringClaim(payload, "picture");
    return findOrCreateFromIdentity(sub, email, name, picture);
  }

  @Transactional
  public UserEntity findOrCreateFromIdentity(String sub, String email, String name, String picture) {
    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("email is required");
    }
    if (sub != null && !sub.isBlank()) {
      Optional<UserEntity> bySub = userRepository.findByGoogleSub(sub);
      if (bySub.isPresent()) {
        return bySub.get();
      }
    }

    Optional<UserEntity> byEmail = userRepository.findByEmail(email);
    if (byEmail.isPresent()) {
      UserEntity user = byEmail.get();
      if (sub != null && !sub.isBlank()) {
        user.setGoogleSub(sub);
      }
      if (picture != null
          && !picture.isBlank()
          && (user.getImagePath() == null || user.getImagePath().isBlank())) {
        user.setImagePath(truncate(picture, 500));
      }
      return userRepository.save(user);
    }

    String baseName = name != null && !name.isBlank() ? name.trim() : deriveNameFromEmail(email);
    String uniqueName = allocateUniqueName(sanitizeName(baseName));
    UserEntity created =
        UserEntity.builder()
            .email(email.trim())
            .name(uniqueName)
            .imagePath(picture != null ? truncate(picture, 500) : "")
            .description("")
            .googleSub(sub != null ? sub : "")
            .build();
    return userRepository.save(created);
  }

  @Transactional
  public UserEntity findOrCreateDevUser(String email, String name) {
    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("email is required");
    }
    return userRepository
        .findByEmail(email.trim())
        .orElseGet(
            () -> {
              String displayName =
                  name != null && !name.isBlank() ? name.trim() : deriveNameFromEmail(email);
              return userRepository.save(
                  UserEntity.builder()
                      .email(email.trim())
                      .name(allocateUniqueName(sanitizeName(displayName)))
                      .imagePath("")
                      .description("")
                      .build());
            });
  }

  private static String deriveNameFromEmail(String email) {
    int at = email.indexOf('@');
    return (at > 0 ? email.substring(0, at) : email).trim();
  }

  private static String deriveDisplayName(Jwt payload, String email) {
    String name = stringClaim(payload, "name");
    if (name != null && !name.isBlank()) {
      return name.trim();
    }
    int at = email.indexOf('@');
    return (at > 0 ? email.substring(0, at) : email).trim();
  }

  private static String stringClaim(Jwt payload, String key) {
    return payload.getClaimAsString(key);
  }

  private String allocateUniqueName(String base) {
    if (userRepository.findByName(base).isEmpty()) {
      return base;
    }
    for (int i = 0; i < MAX_NAME_SUFFIX_ATTEMPTS; i++) {
      int suffix = ThreadLocalRandom.current().nextInt(10_000, 99_999);
      String candidate = truncate(base + "_" + suffix, NAME_MAX_LEN);
      if (userRepository.findByName(candidate).isEmpty()) {
        return candidate;
      }
    }
    throw new IllegalStateException("Could not allocate a unique user name");
  }

  private static String sanitizeName(String raw) {
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) {
      trimmed = "user";
    }
    return truncate(trimmed, NAME_MAX_LEN);
  }

  private static String truncate(String s, int max) {
    if (s.length() <= max) {
      return s;
    }
    return s.substring(0, max);
  }
}
