package com.tripplanning.customfield.dto;

import com.tripplanning.customfield.CustomFieldType;

public final class CustomFieldDtos {

  private CustomFieldDtos() {}

  public record CustomFieldDeclarationDto(
      String id, String name, CustomFieldType type, boolean archived, long createdAt) {}

  public record CreateCustomFieldRequest(String id, String name, String type) {}

  public record ArchiveCustomFieldRequest(boolean archived) {}

  public record TripCustomFieldValueDto(
      String fieldId, String name, CustomFieldType type, String value) {}

  public record UpsertTripCustomFieldValuesRequest(java.util.List<ValueEntry> values) {

    public record ValueEntry(String fieldId, String value) {}
  }
}
