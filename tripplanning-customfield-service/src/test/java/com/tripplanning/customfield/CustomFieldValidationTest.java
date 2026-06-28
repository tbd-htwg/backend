package com.tripplanning.customfield;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CustomFieldValidationTest {

  @Test
  void normalizeIdAcceptsKebabCase() {
    assertThat(CustomFieldValidation.normalizeId("travel-budget")).isEqualTo("travel-budget");
  }

  @Test
  void validateNumberRejectsInvalid() {
    assertThatThrownBy(() -> CustomFieldValidation.validateValue(CustomFieldType.NUMBER, "abc"))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void validateUrlRequiresScheme() {
    assertThatThrownBy(() -> CustomFieldValidation.validateValue(CustomFieldType.URL, "example.com"))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(CustomFieldValidation.validateValue(CustomFieldType.URL, "https://example.com"))
        .isEqualTo("https://example.com");
  }
}
