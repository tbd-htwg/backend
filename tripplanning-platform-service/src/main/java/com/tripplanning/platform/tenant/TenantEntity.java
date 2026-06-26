package com.tripplanning.platform.tenant;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantEntity {

  @Id
  private String id;

  @Column(nullable = false, unique = true, length = 63)
  private String slug;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private TenantTier tier;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private TenantStatus status;

  @Column(name = "host_url", nullable = false, length = 512)
  private String hostUrl;

  @Column(nullable = false, length = 128)
  private String namespace;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "archived_at")
  private Instant archivedAt;

  @Column(name = "db_name", length = 128)
  private String dbName;

  @Column(name = "db_user", length = 128)
  private String dbUser;

  @Column(name = "search_index", length = 128)
  private String searchIndex;

  @Column(name = "firestore_database", length = 128)
  private String firestoreDatabase;

  @Column(name = "gcs_bucket", length = 256)
  private String gcsBucket;

  @Column(name = "provisioning_error", columnDefinition = "TEXT")
  private String provisioningError;

  @Column(name = "estimated_monthly_cost_eur", nullable = false)
  private BigDecimal estimatedMonthlyCostEur;

  @Column(name = "identity_platform_tenant_id", length = 128)
  private String identityPlatformTenantId;

  @Column(name = "enabled_auth_providers", columnDefinition = "TEXT")
  private String enabledAuthProvidersJson;

  @Column(name = "primary_color", length = 32)
  private String primaryColor;

  @Column(name = "header_title")
  private String headerTitle;

  @Column(name = "icon_url", columnDefinition = "TEXT")
  private String iconUrl;

  @Column(name = "title_retract_to_initials", nullable = false)
  private boolean titleRetractToInitials;

  @Column(name = "invert_header_icon", nullable = false)
  private boolean invertHeaderIcon;

  @Column(name = "frontend_path", length = 256)
  private String frontendPath;

  @Column(name = "image_tag", length = 128)
  private String imageTag;

  @Column(name = "provisioning_steps_json", columnDefinition = "TEXT")
  private String provisioningStepsJson;
}
