package com.tripplanning.customfield;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class CustomFieldValidation {

  private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
  private static final int MAX_ID_LENGTH = 63;
  private static final int MAX_NAME_LENGTH = 128;
  private static final int MAX_VALUE_LENGTH = 8192;

  private CustomFieldValidation() {}

  public static String normalizeId(String id) {
    if (id == null || id.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id is required");
    }
    String normalized = id.trim().toLowerCase(Locale.ROOT);
    if (normalized.length() > MAX_ID_LENGTH || !ID_PATTERN.matcher(normalized).matches()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "id must be lowercase alphanumeric segments separated by hyphens (max "
              + MAX_ID_LENGTH
              + " chars)");
    }
    return normalized;
  }

  public static String normalizeName(String name) {
    if (name == null || name.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
    }
    String trimmed = name.trim();
    if (trimmed.length() > MAX_NAME_LENGTH) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "name must be at most " + MAX_NAME_LENGTH + " characters");
    }
    return trimmed;
  }

  public static CustomFieldType parseType(String type) {
    try {
      return CustomFieldType.parse(type);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  public static String validateValue(CustomFieldType type, String value) {
    if (value == null) {
      return "";
    }
    String trimmed = value.trim();
    if (trimmed.length() > MAX_VALUE_LENGTH) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "value must be at most " + MAX_VALUE_LENGTH + " characters");
    }
    if (trimmed.isEmpty()) {
      return "";
    }
    if (type == CustomFieldType.URL
        && !trimmed.startsWith("http://")
        && !trimmed.startsWith("https://")) {
      trimmed = "https://" + trimmed;
    }
    return switch (type) {
      case URL -> {
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST, "URL values must start with http:// or https://");
        }
        yield trimmed;
      }
      case NUMBER -> {
        try {
          Double.parseDouble(trimmed);
        } catch (NumberFormatException e) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid number value");
        }
        yield trimmed;
      }
      case TEXT_SHORT, TEXT_LONG -> trimmed;
    };
  }

  public static String valueDocumentId(long tripId, String fieldId) {
    return tripId + "_" + fieldId;
  }
}
