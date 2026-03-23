package com.tripplanning.api.exception;

import java.util.List;

import org.springframework.http.HttpStatus;

public class EmailAlreadyExistsException extends ApiException {

  public EmailAlreadyExistsException(String message, List<String> details) {
    super(HttpStatus.CONFLICT, "EmailAlreadyExists", message, details);
  }
}

