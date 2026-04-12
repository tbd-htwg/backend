package com.tripplanning.api.controller;

import com.tripplanning.api.dto.request.TripCreateRequest;
import com.tripplanning.api.dto.request.TripPatchRequest;
import com.tripplanning.api.dto.request.TripPutRequest;
import com.tripplanning.api.dto.response.TripDetailsResponse;
import com.tripplanning.api.dto.response.TripListItemResponse;
import com.tripplanning.trip.TripService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1")
public class TripController {

  private final TripService tripService;

  public TripController(TripService tripService) {
    this.tripService = tripService;
  }

  @PostMapping("/trips")
  public ResponseEntity<TripDetailsResponse> create(@Valid @RequestBody TripCreateRequest request) {
    TripDetailsResponse created = tripService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @GetMapping("/trips")
  public ResponseEntity<List<TripListItemResponse>> list() {
    return ResponseEntity.ok(tripService.listTrips());
  }

  @GetMapping("/trips/{id}")
  public ResponseEntity<TripDetailsResponse> get(@PathVariable("id") long id) {
    return ResponseEntity.ok(tripService.getTrip(id));
  }

  @PutMapping("/trips/{id}")
  public ResponseEntity<TripDetailsResponse> replace(
      @PathVariable("id") long id,
      @Valid @RequestBody TripPutRequest request
  ) {
    return ResponseEntity.ok(tripService.replaceTrip(id, request));
  }

  @PatchMapping("/trips/{id}")
  public ResponseEntity<TripDetailsResponse> patch(
      @PathVariable("id") long id,
      @Valid @RequestBody TripPatchRequest request
  ) {
    return ResponseEntity.ok(tripService.patchTrip(id, request));
  }

  @DeleteMapping("/trips/{id}")
  public ResponseEntity<Void> delete(@PathVariable("id") long id) {
    tripService.deleteTrip(id);
    return ResponseEntity.noContent().build();
  }
}

