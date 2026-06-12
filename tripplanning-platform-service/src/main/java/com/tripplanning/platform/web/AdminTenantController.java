package com.tripplanning.platform.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.tripplanning.platform.tenant.TenantDtos;
import com.tripplanning.platform.tenant.TenantService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v2/admin/tenants")
public class AdminTenantController {

  private final TenantService tenantService;

  public AdminTenantController(TenantService tenantService) {
    this.tenantService = tenantService;
  }

  @GetMapping
  public List<TenantDtos.TenantDto> list(
      @RequestParam(defaultValue = "false") boolean includeArchived,
      @RequestParam(required = false) String tier,
      @RequestParam(required = false) String status) {
    return tenantService.list(includeArchived, tier, status);
  }

  @GetMapping("/{id}")
  public TenantDtos.TenantDto get(@PathVariable String id) {
    try {
      return tenantService.getById(id);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  @PostMapping
  public TenantDtos.TenantDto create(@Valid @RequestBody TenantDtos.TenantCreateRequest request) {
    try {
      return tenantService.create(request);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  @PutMapping("/{id}/branding")
  public TenantDtos.TenantDto updateBranding(
      @PathVariable String id, @RequestBody TenantDtos.TenantBrandingUpdateRequest request) {
    try {
      return tenantService.updateBranding(id, request);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  @PostMapping("/{id}/archive")
  public TenantDtos.TenantDto archive(@PathVariable String id) {
    try {
      return tenantService.archive(id);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalStateException e) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
    }
  }

  @GetMapping("/{id}/users")
  public List<TenantDtos.TenantUserDto> listUsers(@PathVariable String id) {
    tenantService.getById(id);
    return List.of();
  }

  @PostMapping("/{id}/retry")
  public void retry(@PathVariable String id) {
    try {
      tenantService.retry(id);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalStateException e) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
    }
  }
}
