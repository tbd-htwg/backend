package com.tripplanning.trip.read;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Ensures {@link ResponseStatusException} statuses (e.g. 404) are written to the response. Without
 * this, the OAuth2 resource-server entry point can turn them into 401 for anonymous callers.
 */
@RestControllerAdvice(assignableTypes = TripFeedController.class)
public class TripFeedExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Void> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).build();
    }
}
