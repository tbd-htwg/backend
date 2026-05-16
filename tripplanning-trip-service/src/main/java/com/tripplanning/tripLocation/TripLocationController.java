package com.tripplanning.tripLocation;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.tripplanning.trip.TripRepository;
import com.tripplanning.user.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v2/trip-locations")
@RequiredArgsConstructor
public class TripLocationController {

    private final TripLocationService tripLocationService;
    private final TripRepository tripRepository;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<TripLocationEntity> createTripLocation(
            @Valid @RequestBody TripLocationRequest.CreateTripLocationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        var trip = tripRepository
                .findById(request.tripId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));

        if (!userService.isCurrentUser(trip.getUser())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized.");
        }

        return ResponseEntity.ok(tripLocationService.addStop(request));
    }

    @GetMapping("/{id}/details")
    public ResponseEntity<?> getStopDetails(@PathVariable Long id) {
        return ResponseEntity.ok(tripLocationService.getExternalDetails(id));
    }
}
