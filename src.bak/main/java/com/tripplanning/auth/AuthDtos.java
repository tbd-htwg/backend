package com.tripplanning.auth;

public final class AuthDtos {

  private AuthDtos() {}

  public record GoogleLoginRequest(String credential) {}

  public record DevLoginRequest(String email, String name) {}

  public record UserResponseDto(
      long id, String email, String name, String imageUrl, String description) {}

  public record LoginResponse(String tokenType, String accessToken, UserResponseDto user) {}
}
