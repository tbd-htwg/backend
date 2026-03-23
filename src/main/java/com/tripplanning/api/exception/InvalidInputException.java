package com.tripplanning.api.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

public class InvalidInputException extends ApiException {

  public InvalidInputException(String message, List<String> details) {
    super(HttpStatus.BAD_REQUEST, "InvalidInput", message, details);
  }
}

