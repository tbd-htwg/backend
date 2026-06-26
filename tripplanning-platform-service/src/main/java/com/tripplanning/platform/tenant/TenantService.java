package com.tripplanning.platform.tenant;

import java.time.Instant;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripplanning.platform.branding.BrandingIconService;
import com.tripplanning.platform.config.PlatformProperties;
import com.tripplanning.platform.provisioning.ProvisioningStepDefinitions;
import com.tripplanning.platform.provisioning.TenantProvisioningService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TenantService {

  private final TenantRepository tenantRepository;
  private final TenantMapper tenantMapper;
  private final TenantSlugValidator slugValidator;
  private final ProvisioningJson provisioningJson;
  private final PlatformProperties platformProperties;
  private final TenantProvisioningService provisioningService;
  private final ApplicationEventPublisher eventPublisher;
  private final BrandingIconService brandingIconService;

  public List<TenantDtos.TenantDto> list(boolean includeArchived, String tierFilter, String statusFilter) {
    return tenantRepository.findAllByOrderByCreatedAtDesc().stream()
        .filter(t -> includeArchived || t.getStatus() != TenantStatus.ARCHIVED)
        .filter(t -> tierFilter == null || t.getTier().name().equalsIgnoreCase(tierFilter))
        .filter(t -> statusFilter == null || t.getStatus().name().equalsIgnoreCase(statusFilter))
        .map(tenantMapper::toDto)
        .toList();
  }

  public TenantDtos.TenantDto getById(String id) {
    return tenantRepository
        .findById(id)
        .map(tenantMapper::toDto)
        .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
  }

  public TenantDtos.PublicTenantConfigDto getPublicConfig(String slug) {
    TenantEntity entity =
        tenantRepository
            .findBySlug(slug)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
    return tenantMapper.toPublicConfig(entity);
  }

  public TenantDtos.SlugAvailabilityDto checkSlugAvailability(String slug) {
    String normalized = slug == null ? "" : slug.trim().toLowerCase();
    if (normalized.isBlank()) {
      return new TenantDtos.SlugAvailabilityDto(false, "Slug is required");
    }
    try {
      slugValidator.validate(normalized);
    } catch (IllegalArgumentException e) {
      return new TenantDtos.SlugAvailabilityDto(false, e.getMessage());
    }
    if (tenantRepository.existsBySlug(normalized)) {
      return new TenantDtos.SlugAvailabilityDto(false, "Tenant slug already exists");
    }
    return new TenantDtos.SlugAvailabilityDto(true, null);
  }

  @Transactional
  public TenantDtos.TenantDto create(TenantDtos.TenantCreateRequest request) {
    String slug = request.slug().trim().toLowerCase();
    String displayName = request.displayName().trim();
    slugValidator.validate(slug);
    if (displayName.contains("\n") || displayName.contains("\r")) {
      throw new IllegalArgumentException("Display name must be a single line");
    }
    if (tenantRepository.existsBySlug(slug)) {
      throw new IllegalArgumentException("Tenant slug already exists: " + slug);
    }

    TenantTier tier = TenantTier.valueOf(request.tier().trim().toUpperCase());
    if (tier == TenantTier.FREE) {
      throw new IllegalArgumentException("Free tier cannot be created via admin API");
    }
    validateGeneratedResourceNames(slug, tier);

    Instant now = Instant.now();
    String id = "tenant-" + slug;
    String hostBase = platformProperties.getHostBase();
    String enterpriseHostBase = platformProperties.getEnterpriseHostBase();

    TenantEntity entity =
        TenantEntity.builder()
            .id(id)
            .slug(slug)
            .displayName(displayName)
            .tier(tier)
            .status(TenantStatus.PROVISIONING)
            .hostUrl(TenantNaming.hostUrl(slug, tier, hostBase, enterpriseHostBase))
            .namespace(TenantNaming.namespace(slug, tier))
            .createdAt(now)
            .updatedAt(now)
            .dbName(TenantNaming.dbName(slug, tier))
            .dbUser(TenantNaming.dbUser(slug, tier))
            .searchIndex(TenantNaming.searchIndex(slug, tier))
            .estimatedMonthlyCostEur(TenantNaming.estimatedCost(tier))
            .headerTitle(displayName)
            .frontendPath(TenantNaming.frontendPath(slug, tier))
            .imageTag(TenantNaming.imageTag(slug, tier))
            .gcsBucket(TenantNaming.gcsBucket(slug, tier))
            .provisioningStepsJson(
                provisioningJson.writeSteps(
                    ProvisioningStepDefinitions.initialSteps(
                        tier, platformProperties.getProvisioning().isUseStubs())))
            .build();

    tenantRepository.save(entity);
    eventPublisher.publishEvent(new TenantProvisioningRequested(id));
    return tenantMapper.toDto(entity);
  }

  private static void validateGeneratedResourceNames(String slug, TenantTier tier) {
    if (tier == TenantTier.STANDARD && slug.length() > 46) {
      throw new IllegalArgumentException(
          "Standard tenant slug must be at most 46 characters");
    }
    if (tier == TenantTier.ENTERPRISE && slug.length() > 32) {
      throw new IllegalArgumentException(
          "Enterprise tenant slug must be at most 32 characters");
    }
  }

  @Transactional
  public TenantDtos.TenantDto updateBranding(String id, TenantDtos.TenantBrandingUpdateRequest request) {
    TenantEntity entity =
        tenantRepository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
    if (request.headerTitle() != null) {
      entity.setHeaderTitle(
          request.headerTitle().isBlank() ? entity.getDisplayName() : request.headerTitle());
    }
    entity.setPrimaryColor(request.primaryColor());
    if (request.iconUrl() != null) {
      brandingIconService.deleteStoredIcon(entity, entity.getIconUrl());
      entity.setIconUrl(request.iconUrl().isBlank() ? null : request.iconUrl());
    }
    if (request.titleRetractToInitials() != null) {
      entity.setTitleRetractToInitials(request.titleRetractToInitials());
    }
    if (request.invertHeaderIcon() != null) {
      entity.setInvertHeaderIcon(request.invertHeaderIcon());
    }
    entity.setUpdatedAt(Instant.now());
    return tenantMapper.toDto(tenantRepository.save(entity));
  }

  @Transactional
  public TenantDtos.BrandingIconUploadResponse uploadBrandingIcon(
      String id, TenantDtos.BrandingIconUploadRequest request) {
    TenantEntity entity =
        tenantRepository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
    BrandingIconService.SignedUploadInfo signedUpload =
        brandingIconService.createSignedUpload(entity, request.fileName(), request.contentType());
    if (brandingIconService.usesStubStorage(entity)) {
      return new TenantDtos.BrandingIconUploadResponse(
          signedUpload.uploadUrl(), "", signedUpload.objectName(), signedUpload.contentType());
    }
    brandingIconService.deleteStoredIcon(entity, entity.getIconUrl());
    entity.setIconUrl(signedUpload.objectName());
    entity.setUpdatedAt(Instant.now());
    tenantRepository.save(entity);
    String signedReadUrl = brandingIconService.resolveIconUrl(entity, signedUpload.objectName());
    return new TenantDtos.BrandingIconUploadResponse(
        signedUpload.uploadUrl(), signedReadUrl, signedUpload.objectName(), signedUpload.contentType());
  }

  @Transactional
  public String completeStubBrandingIconUpload(String tenantId, String token, byte[] body) {
    TenantEntity entity =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
    brandingIconService.deleteStoredIcon(entity, entity.getIconUrl());
    String dataUrl = brandingIconService.completeStubUpload(tenantId, token, body);
    entity.setIconUrl(dataUrl);
    entity.setUpdatedAt(Instant.now());
    tenantRepository.save(entity);
    return dataUrl;
  }

  @Transactional
  public TenantDtos.TenantDto archive(String id) {
    TenantEntity entity =
        tenantRepository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
    if ("free".equals(entity.getSlug())) {
      throw new IllegalStateException("Free pool cannot be archived");
    }
    entity.setStatus(TenantStatus.ARCHIVED);
    entity.setArchivedAt(Instant.now());
    entity.setUpdatedAt(Instant.now());
    return tenantMapper.toDto(tenantRepository.save(entity));
  }

  @Transactional
  public boolean deleteBySlug(String slug) {
    return tenantRepository
        .findBySlug(slug.toLowerCase())
        .map(
            tenant -> {
              if ("free".equals(tenant.getSlug())) {
                throw new IllegalStateException("Free pool cannot be deleted");
              }
              tenantRepository.delete(tenant);
              return true;
            })
        .orElse(false);
  }

  public void retry(String id) {
    provisioningService.retry(id);
  }
}
