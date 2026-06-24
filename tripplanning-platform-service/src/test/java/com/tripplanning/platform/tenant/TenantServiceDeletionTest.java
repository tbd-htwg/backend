package com.tripplanning.platform.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.tripplanning.platform.config.PlatformProperties;
import com.tripplanning.platform.provisioning.TenantProvisioningService;

@ExtendWith(MockitoExtension.class)
class TenantServiceDeletionTest {

  @Mock private TenantRepository tenantRepository;
  @Mock private TenantMapper tenantMapper;
  @Mock private TenantSlugValidator slugValidator;
  @Mock private ProvisioningJson provisioningJson;
  @Mock private PlatformProperties platformProperties;
  @Mock private TenantProvisioningService provisioningService;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private TenantService tenantService;

  @Test
  void deletesExistingTenantByNormalizedSlug() {
    TenantEntity tenant = TenantEntity.builder().id("tenant-acme").slug("acme").build();
    when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(tenant));

    assertThat(tenantService.deleteBySlug("ACME")).isTrue();

    verify(tenantRepository).delete(tenant);
  }

  @Test
  void missingTenantIsAlreadyAbsent() {
    when(tenantRepository.findBySlug("missing")).thenReturn(Optional.empty());

    assertThat(tenantService.deleteBySlug("missing")).isFalse();
  }

  @Test
  void freePoolCannotBeDeleted() {
    TenantEntity tenant = TenantEntity.builder().id("tenant-free").slug("free").build();
    when(tenantRepository.findBySlug("free")).thenReturn(Optional.of(tenant));

    assertThatThrownBy(() -> tenantService.deleteBySlug("free"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Free pool cannot be deleted");

    verify(tenantRepository, never()).delete(tenant);
  }
}
