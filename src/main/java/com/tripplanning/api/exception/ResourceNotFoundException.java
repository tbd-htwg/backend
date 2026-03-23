package com.tripplanning.api.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

public class ResourceNotFoundException extends ApiException {

  public ResourceNotFoundException(String message, List<String> details) {
    super(HttpStatus.NOT_FOUND, "NotFound", message, details);
  }
}

