package com.tripplanning.platform.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import com.tripplanning.platform.client.CustomFieldClient;
import com.tripplanning.platform.client.CustomFieldClient.ArchiveCustomFieldRequest;
import com.tripplanning.platform.client.CustomFieldClient.CreateCustomFieldRequest;
import com.tripplanning.platform.client.CustomFieldClient.CustomFieldDeclarationResponse;
import com.tripplanning.platform.tenant.TenantEntity;
import com.tripplanning.platform.tenant.TenantRepository;
import com.tripplanning.platform.tenant.TenantTier;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/v2/admin/tenants/{tenantId}/custom-fields")
public class AdminCustomFieldController {

  private final TenantRepository tenantRepository;
  private final CustomFieldClient customFieldClient;

  public AdminCustomFieldController(
      TenantRepository tenantRepository, CustomFieldClient customFieldClient) {
    this.tenantRepository = tenantRepository;
    this.customFieldClient = customFieldClient;
  }

  @GetMapping
  public List<CustomFieldDeclarationResponse> list(@PathVariable String tenantId) {
    TenantEntity tenant = loadTenant(tenantId);
    return invoke(() -> customFieldClient.list(tenant));
  }

  @PostMapping
  public CustomFieldDeclarationResponse create(
      @PathVariable String tenantId, @Valid @RequestBody AdminCreateCustomFieldRequest request) {
    TenantEntity tenant = loadTenant(tenantId);
    return invoke(
        () ->
            customFieldClient.create(
                tenant, new CreateCustomFieldRequest(request.id(), request.name(), request.type())));
  }

  @PatchMapping("/{fieldId}")
  public CustomFieldDeclarationResponse archive(
      @PathVariable String tenantId,
      @PathVariable String fieldId,
      @Valid @RequestBody AdminArchiveCustomFieldRequest request) {
    TenantEntity tenant = loadTenant(tenantId);
    return invoke(
        () -> customFieldClient.archive(tenant, fieldId, request.archived()));
  }

  private TenantEntity loadTenant(String tenantId) {
    TenantEntity tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    if (tenant.getTier() != TenantTier.ENTERPRISE && tenant.getTier() != TenantTier.DEVELOP) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Custom fields are only available for enterprise tenants");
    }
    return tenant;
  }

  private <T> T invoke(java.util.function.Supplier<T> action) {
    try {
      return action.get();
    } catch (WebClientResponseException e) {
      if (e.getStatusCode().is4xxClientError()) {
        throw new ResponseStatusException(HttpStatus.valueOf(e.getStatusCode().value()), e.getResponseBodyAsString());
      }
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Custom field service unavailable");
    } catch (IllegalStateException e) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
    }
  }

  public record AdminCreateCustomFieldRequest(
      @NotBlank String id, @NotBlank String name, @NotBlank String type) {}

  public record AdminArchiveCustomFieldRequest(boolean archived) {}
}
