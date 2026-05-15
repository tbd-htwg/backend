package com.tripplanning.auth;

import org.springframework.stereotype.Component;

import com.tripplanning.images.ImageService;
import com.tripplanning.user.UserEntity;

@Component
final class UserResponseMapper {

  private final ImageService imageService;

  UserResponseMapper(ImageService imageService) {
    this.imageService = imageService;
  }

  AuthDtos.UserResponseDto fromEntity(UserEntity e) {
    String imageUrl = imageService.createSignedReadUrlIfAuthenticated(e.getImagePath());
    return new AuthDtos.UserResponseDto(
        e.getId(),
        e.getEmail() != null ? e.getEmail() : "",
        e.getName() != null ? e.getName() : "",
        imageUrl != null ? imageUrl : "",
        e.getDescription() != null ? e.getDescription() : "");
  }
}
