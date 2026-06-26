package com.tripplanning.platform.tenant;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import com.tripplanning.platform.branding.BrandingIconService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TenantMapper {

  private final ProvisioningJson provisioningJson;
  private final BrandingIconService brandingIconService;

  public TenantDtos.TenantDto toDto(TenantEntity entity) {
    return toDto(entity, Collections.emptyList());
  }

  public TenantDtos.TenantDto toDto(TenantEntity entity, List<TenantDtos.TenantUserDto> users) {
    return new TenantDtos.TenantDto(
        entity.getId(),
        entity.getSlug(),
        entity.getDisplayName(),
        entity.getTier().name(),
        entity.getStatus().name(),
        entity.getHostUrl(),
        entity.getNamespace(),
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        entity.getArchivedAt(),
        entity.getDbName(),
        entity.getSearchIndex(),
        entity.getFirestoreDatabase(),
        entity.getGcsBucket(),
        entity.getProvisioningError(),
        entity.getEstimatedMonthlyCostEur(),
        provisioningJson.readSteps(entity.getProvisioningStepsJson()),
        users,
        entity.getPrimaryColor(),
        entity.getHeaderTitle(),
        resolveIconUrl(entity),
        entity.isTitleRetractToInitials(),
        entity.isInvertHeaderIcon(),
        entity.getFrontendPath(),
        entity.getImageTag());
  }

  public TenantDtos.PublicTenantConfigDto toPublicConfig(TenantEntity entity) {
    return new TenantDtos.PublicTenantConfigDto(
        entity.getSlug(),
        entity.getTier().name(),
        entity.getStatus().name(),
        entity.getHostUrl(),
        entity.getIdentityPlatformTenantId(),
        provisioningJson.readProviders(entity.getEnabledAuthProvidersJson()),
        entity.getPrimaryColor(),
        entity.getHeaderTitle() != null ? entity.getHeaderTitle() : entity.getDisplayName(),
        resolveIconUrl(entity),
        entity.isTitleRetractToInitials(),
        entity.isInvertHeaderIcon(),
        entity.getFrontendPath());
  }

  private String resolveIconUrl(TenantEntity entity) {
    try {
      return brandingIconService.resolveIconUrl(entity, entity.getIconUrl());
    } catch (IllegalStateException e) {
      return entity.getIconUrl();
    }
  }
}
