package com.tripplanning.customfield.internal;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripplanning.customfield.CustomFieldValidation;
import com.tripplanning.customfield.FirestoreCustomFieldService;
import com.tripplanning.customfield.TenantContextFilter;
import com.tripplanning.customfield.dto.CustomFieldDtos.ArchiveCustomFieldRequest;
import com.tripplanning.customfield.dto.CustomFieldDtos.CreateCustomFieldRequest;
import com.tripplanning.customfield.dto.CustomFieldDtos.CustomFieldDeclarationDto;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal/tenants/{slug}/custom-fields")
@RequiredArgsConstructor
public class InternalCustomFieldController {

  private final FirestoreCustomFieldService firestoreCustomFieldService;

  @GetMapping
  public List<CustomFieldDeclarationDto> list(@PathVariable String slug) {
    return firestoreCustomFieldService.listDeclarations(normalize(slug), true);
  }

  @PostMapping
  public CustomFieldDeclarationDto create(
      @PathVariable String slug, @Valid @RequestBody CreateCustomFieldRequest request) {
    return firestoreCustomFieldService.createDeclaration(
        normalize(slug),
        CustomFieldValidation.normalizeId(request.id()),
        CustomFieldValidation.normalizeName(request.name()),
        CustomFieldValidation.parseType(request.type()));
  }

  @PatchMapping("/{fieldId}")
  public CustomFieldDeclarationDto archive(
      @PathVariable String slug,
      @PathVariable String fieldId,
      @Valid @RequestBody ArchiveCustomFieldRequest request) {
    return firestoreCustomFieldService.setArchived(
        normalize(slug), CustomFieldValidation.normalizeId(fieldId), request.archived());
  }

  private static String normalize(String slug) {
    return TenantContextFilter.normalizeSlug(slug);
  }
}
