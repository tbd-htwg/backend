package com.tripplanning.api.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ApiErrorResponse {

  private final String error;
  private final String message;
  private final List<String> details;
}

