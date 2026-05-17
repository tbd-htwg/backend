package com.tripplanning.auth;

public final class AuthDtos {

  private AuthDtos() {}

  /** Firebase ID token from any Identity Platform sign-in (Google, email/password, etc.). */
  public record FirebaseLoginRequest(String credential) {}

  /** @deprecated Use {@link FirebaseLoginRequest}; kept for existing clients. */
  @Deprecated
  public record GoogleLoginRequest(String credential) {}

  public record DevLoginRequest(String email, String name) {}

  public record UserResponseDto(
      long id, String email, String name, String imageUrl, String description) {}

  public record LoginResponse(String tokenType, String accessToken, UserResponseDto user) {}
}
