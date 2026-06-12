package com.tripplanning.internal;

public final class InternalUserDtos {

  private InternalUserDtos() {}

  public record IdentityProvisionRequest(String sub, String email, String name, String picture) {}

  public record DevProvisionRequest(String email, String name) {}
}
