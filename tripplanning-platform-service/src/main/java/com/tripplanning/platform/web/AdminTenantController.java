package com.tripplanning.platform.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.tripplanning.platform.client.TripUserClient;
import com.tripplanning.platform.tenant.TenantDtos;
import com.tripplanning.platform.tenant.TenantService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v2/admin/tenants")
public class AdminTenantController {

  private final TenantService tenantService;
  private final TripUserClient tripUserClient;

  public AdminTenantController(TenantService tenantService, TripUserClient tripUserClient) {
    this.tenantService = tenantService;
    this.tripUserClient = tripUserClient;
  }

  @GetMapping
  public List<TenantDtos.TenantDto> list(
      @RequestParam(defaultValue = "false") boolean includeArchived,
      @RequestParam(required = false) String tier,
      @RequestParam(required = false) String status) {
    return tenantService.list(includeArchived, tier, status);
  }

  @GetMapping("/slug-availability")
  public TenantDtos.SlugAvailabilityDto slugAvailability(@RequestParam String slug) {
    return tenantService.checkSlugAvailability(slug);
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

  @PutMapping("/{id}/resources")
  public TenantDtos.TenantDto updateResources(
      @PathVariable String id, @RequestBody TenantDtos.TenantResourceConfigDto request) {
    try {
      return tenantService.updateResources(id, request);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    } catch (IllegalStateException e) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
    }
  }

  @PostMapping("/{id}/branding/icon")
  public TenantDtos.BrandingIconUploadResponse uploadBrandingIcon(
      @PathVariable String id, @RequestBody TenantDtos.BrandingIconUploadRequest request) {
    try {
      return tenantService.uploadBrandingIcon(id, request);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    } catch (IllegalStateException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  @PutMapping("/{id}/branding/icon/stub-upload/{token}")
  public TenantDtos.BrandingIconUploadResponse completeStubBrandingIconUpload(
      @PathVariable String id,
      @PathVariable String token,
      HttpServletRequest request) {
    try {
      byte[] body = request.getInputStream().readAllBytes();
      String dataUrl = tenantService.completeStubBrandingIconUpload(id, token, body);
      return new TenantDtos.BrandingIconUploadResponse("", dataUrl, dataUrl, request.getContentType());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    } catch (java.io.IOException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read upload body");
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
    TenantDtos.TenantDto tenant = tenantService.getById(id);
    String host = TripUserClient.hostHeaderFromUrl(tenant.hostUrl());
    try {
      List<TenantDtos.TenantUserDto> users = tripUserClient.listUsers(host);
      return users != null ? users : List.of();
    } catch (Exception e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Failed to list tenant users: " + e.getMessage());
    }
  }

  @DeleteMapping("/{id}/users/{userId}")
  public void deleteUser(@PathVariable String id, @PathVariable long userId) {
    TenantDtos.TenantDto tenant = tenantService.getById(id);
    String host = TripUserClient.hostHeaderFromUrl(tenant.hostUrl());
    try {
      tripUserClient.deleteUser(host, userId);
    } catch (WebClientResponseException e) {
      if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
      }
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Failed to delete tenant user: " + e.getMessage());
    } catch (Exception e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Failed to delete tenant user: " + e.getMessage());
    }
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
