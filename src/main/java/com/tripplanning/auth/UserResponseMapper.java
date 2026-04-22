package com.tripplanning.auth;

import com.tripplanning.user.UserEntity;

final class UserResponseMapper {

  private UserResponseMapper() {}

  static AuthDtos.UserResponseDto fromEntity(UserEntity e) {
    return new AuthDtos.UserResponseDto(
        e.getId(),
        e.getEmail() != null ? e.getEmail() : "",
        e.getName() != null ? e.getName() : "",
        e.getImageUrl() != null ? e.getImageUrl() : "",
        e.getDescription() != null ? e.getDescription() : "");
  }
}
