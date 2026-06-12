package com.tripplanning.platform.tenant;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class TenantDtos {

  private TenantDtos() {}

  public record ProvisioningStepDto(
      String key, String label, String status) {}

  public record TenantUserDto(long id, String name, String email, String description) {}

  public record TenantDto(
      String id,
      String slug,
      String displayName,
      String tier,
      String status,
      String hostUrl,
      String namespace,
      Instant createdAt,
      Instant updatedAt,
      Instant archivedAt,
      String dbName,
      String searchIndex,
      String firestoreDatabase,
      String gcsBucket,
      String provisioningError,
      BigDecimal estimatedMonthlyCostEur,
      List<ProvisioningStepDto> provisioningSteps,
      List<TenantUserDto> users,
      String primaryColor,
      String headerTitle,
      String iconUrl,
      String frontendPath,
      String imageTag) {}

  public record TenantCreateRequest(
      @NotBlank
          @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$")
          @Size(max = 63)
          String slug,
      @NotBlank @Size(max = 255) String displayName,
      @NotBlank String tier) {}

  public record TenantBrandingUpdateRequest(
      String primaryColor, String headerTitle, String iconUrl) {}

  public record PublicTenantConfigDto(
      String slug,
      String tier,
      String status,
      String hostUrl,
      String identityPlatformTenantId,
      List<String> enabledAuthProviders,
      String primaryColor,
      String headerTitle,
      String iconUrl,
      String frontendPath) {}

  public record AuthConfigDto(
      String identityPlatformTenantId, List<String> enabledAuthProviders) {}

  public record UserResponseDto(
      long id, String email, String name, String imageUrl, String description) {}

  public record LoginResponse(String tokenType, String accessToken, UserResponseDto user) {}

  public record FirebaseLoginRequest(String credential) {}

  /** @deprecated Use {@link FirebaseLoginRequest}. */
  @Deprecated
  public record GoogleLoginRequest(String credential) {}

  public record DevLoginRequest(String email, String name) {}
}
