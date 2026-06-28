package com.tripplanning.customfield;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripplanning.common.tenant.TenantContextHolder;
import com.tripplanning.customfield.dto.CustomFieldDtos.CustomFieldDeclarationDto;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v2/custom-fields")
@RequiredArgsConstructor
public class CustomFieldController {

  private final FirestoreCustomFieldService firestoreCustomFieldService;

  @GetMapping
  public List<CustomFieldDeclarationDto> listActive() {
    return firestoreCustomFieldService.listDeclarations(TenantContextHolder.slugOrDefault(), false);
  }
}
