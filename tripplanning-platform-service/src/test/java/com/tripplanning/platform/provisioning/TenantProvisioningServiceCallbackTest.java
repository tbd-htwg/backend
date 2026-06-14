package com.tripplanning.platform.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanning.platform.config.PlatformProperties;
import com.tripplanning.platform.infra.IdentityPlatformClient;
import com.tripplanning.platform.infra.StandardDataProvisioner;
import com.tripplanning.platform.infra.TenantInfrastructureProvisioner;
import com.tripplanning.platform.tenant.ProvisioningJson;
import com.tripplanning.platform.tenant.TenantEntity;
import com.tripplanning.platform.tenant.TenantRepository;
import com.tripplanning.platform.tenant.TenantStatus;
import com.tripplanning.platform.tenant.TenantTier;

@ExtendWith(MockitoExtension.class)
class TenantProvisioningServiceCallbackTest {

  @Mock private TenantRepository tenantRepository;
  @Mock private IdentityPlatformClient identityPlatformClient;
  @Mock private TenantInfrastructureProvisioner infrastructureProvisioner;
  @Mock private StandardDataProvisioner standardDataProvisioner;

  private TenantProvisioningService service;
  private TenantEntity standardTenant;

  @BeforeEach
  void setUp() {
    PlatformProperties properties = new PlatformProperties();
    properties.getProvisioning().setUseStubs(false);
    service =
        new TenantProvisioningService(
            tenantRepository,
            new ProvisioningJson(new ObjectMapper()),
            identityPlatformClient,
            infrastructureProvisioner,
            standardDataProvisioner,
            properties);

    standardTenant =
        TenantEntity.builder()
            .id("tenant-acme")
            .slug("acme")
            .displayName("Acme")
            .tier(TenantTier.STANDARD)
            .status(TenantStatus.PROVISIONING)
            .hostUrl("https://acme.k8s.tbd-htwg.de")
            .namespace("tripplanning-standard")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .dbName("tripplanning_std_acme")
            .dbUser("tripplanning_app_acme")
            .searchIndex("tripentity-acme")
            .estimatedMonthlyCostEur(BigDecimal.TEN)
            .provisioningStepsJson("[]")
            .build();
  }

  @Test
  void successCompletesStandardTenant() {
    when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(standardTenant));
    when(tenantRepository.findById("tenant-acme")).thenReturn(Optional.of(standardTenant));
    when(tenantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ProvisioningCallbackOutcome outcome =
        service.completeProvisioningFromCallback(
            "acme", new ProvisioningCallbackRequest("SUCCESS", null, null, null, null, null, null, null));

    assertThat(outcome).isEqualTo(ProvisioningCallbackOutcome.COMPLETED);
    verify(standardDataProvisioner).createSearchIndex("tripentity-acme");
    verify(standardDataProvisioner, never()).createDatabase(anyString());

    ArgumentCaptor<TenantEntity> saved = ArgumentCaptor.forClass(TenantEntity.class);
    verify(tenantRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
    assertThat(saved.getAllValues().stream().anyMatch(t -> t.getStatus() == TenantStatus.ACTIVE))
        .isTrue();
  }

  @Test
  void failureMarksTenantFailed() {
    when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(standardTenant));
    when(tenantRepository.findById("tenant-acme")).thenReturn(Optional.of(standardTenant));
    when(tenantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ProvisioningCallbackOutcome outcome =
        service.completeProvisioningFromCallback(
            "acme",
            new ProvisioningCallbackRequest(
                "FAILED", "Terraform apply failed", "terraform_infra", null, null, null, null, null));

    assertThat(outcome).isEqualTo(ProvisioningCallbackOutcome.COMPLETED);
    ArgumentCaptor<TenantEntity> saved = ArgumentCaptor.forClass(TenantEntity.class);
    verify(tenantRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
    assertThat(saved.getAllValues().stream().anyMatch(t -> t.getStatus() == TenantStatus.FAILED))
        .isTrue();
  }

  @Test
  void alreadyActiveIsIdempotent() {
    standardTenant.setStatus(TenantStatus.ACTIVE);
    when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(standardTenant));

    ProvisioningCallbackOutcome outcome =
        service.completeProvisioningFromCallback(
            "acme", new ProvisioningCallbackRequest("SUCCESS", null, null, null, null, null, null, null));

    assertThat(outcome).isEqualTo(ProvisioningCallbackOutcome.ALREADY_ACTIVE);
    verify(tenantRepository, never()).save(any());
  }

  @Test
  void rejectsCallbackForFailedTenant() {
    standardTenant.setStatus(TenantStatus.FAILED);
    when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(standardTenant));

    assertThatThrownBy(
            () ->
                service.completeProvisioningFromCallback(
                    "acme",
                    new ProvisioningCallbackRequest("SUCCESS", null, null, null, null, null, null, null)))
        .isInstanceOf(IllegalStateException.class);
  }
}
