package com.tripplanning.api.exception;

import org.springframework.http.HttpStatus;
import lombok.Getter;

import java.util.List;

@Getter
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String error;
  private final List<String> details;

  public ApiException(HttpStatus status, String error, String message, List<String> details) {
    super(message);
    this.status = status;
    this.error = error;
    this.details = details;
  }
}

