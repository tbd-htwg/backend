package com.tripplanning.api.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex, WebRequest request) {
    ApiErrorResponse body = new ApiErrorResponse(ex.getError(), ex.getMessage(), ex.getDetails());
    return ResponseEntity.status(ex.getStatus()).body(body);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    List<String> fields = ex.getBindingResult().getFieldErrors().stream()
        .map(err -> err.getField())
        .distinct()
        .collect(Collectors.toList());

    ApiErrorResponse body = new ApiErrorResponse(
        "InvalidInput",
        "Invalid request.",
        fields
    );
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
    ApiErrorResponse body = new ApiErrorResponse(
        "InvalidInput",
        "Malformed JSON request.",
        List.of("request")
    );
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    ApiErrorResponse body = new ApiErrorResponse(
        "InvalidInput",
        "Invalid request parameter.",
        List.of(ex.getName())
    );
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }
}

