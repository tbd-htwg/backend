package com.tripplanning.customfield;

public enum CustomFieldType {
  TEXT_SHORT,
  TEXT_LONG,
  URL,
  NUMBER;

  public static CustomFieldType parse(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("type is required");
    }
    try {
      return CustomFieldType.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unknown custom field type: " + raw);
    }
  }
}
