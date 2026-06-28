package com.tripplanning.platform.web;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.tripplanning.platform.tenant.PlatformAdminEntity;
import com.tripplanning.platform.tenant.PlatformAdminRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/v2/admin/platform-admins")
public class AdminPlatformAdminsController {

  private final PlatformAdminRepository repository;

  public AdminPlatformAdminsController(PlatformAdminRepository repository) {
    this.repository = repository;
  }

  @GetMapping
  public List<PlatformAdminDto> list() {
    return repository.findAllByOrderByEmailAsc().stream().map(PlatformAdminDto::from).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PlatformAdminDto add(@Valid @RequestBody AddPlatformAdminRequest request) {
    String email = normalizeEmail(request.email());
    return repository
        .findByEmailIgnoreCase(email)
        .map(PlatformAdminDto::from)
        .orElseGet(
            () -> {
              try {
                return PlatformAdminDto.from(
                    repository.save(new PlatformAdminEntity(email, Instant.now())));
              } catch (DataIntegrityViolationException e) {
                return repository
                    .findByEmailIgnoreCase(email)
                    .map(PlatformAdminDto::from)
                    .orElseThrow(() -> e);
              }
            });
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void remove(@PathVariable long id, JwtAuthenticationToken authentication) {
    PlatformAdminEntity admin =
        repository
            .findById(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Administrator not found"));
    String currentEmail = authentication.getToken().getClaimAsString("email");
    if (currentEmail != null && admin.getEmail().equalsIgnoreCase(currentEmail)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "You cannot remove your own administrator access");
    }
    if (repository.count() <= 1) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "At least one platform administrator is required");
    }
    repository.delete(admin);
  }

  private static String normalizeEmail(String email) {
    return email.trim().toLowerCase();
  }

  public record AddPlatformAdminRequest(@NotBlank @Email String email) {}

  public record PlatformAdminDto(long id, String email, Instant createdAt) {
    private static PlatformAdminDto from(PlatformAdminEntity entity) {
      return new PlatformAdminDto(entity.getId(), entity.getEmail(), entity.getCreatedAt());
    }
  }
}
