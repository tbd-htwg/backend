package com.tripplanning.api.controller;

import com.tripplanning.api.dto.TripCreateRequest;
import com.tripplanning.api.dto.TripDetailsResponse;
import com.tripplanning.api.dto.TripListItemResponse;
import com.tripplanning.trip.TripService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
}

