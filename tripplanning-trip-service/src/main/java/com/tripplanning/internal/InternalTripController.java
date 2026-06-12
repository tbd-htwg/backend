package com.tripplanning.internal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripplanning.trip.TripRepository;
import com.tripplanning.trip.read.TripFeedCachedReader;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalTripController {

    private final TripFeedCachedReader tripFeedCachedReader;
    private final TripRepository tripRepository;

    @RequestMapping(method = org.springframework.web.bind.annotation.RequestMethod.HEAD, path = "/trips/{id}")
    public ResponseEntity<Void> tripExists(@PathVariable("id") long tripId) {
        return tripFeedCachedReader.tripExists(tripId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @GetMapping("/trips/{id}/owner-user-id")
    public ResponseEntity<Long> tripOwnerUserId(@PathVariable("id") long tripId) {
        return tripRepository
                .findById(tripId)
                .map(t -> ResponseEntity.ok(t.getUser().getId()))
                .orElse(ResponseEntity.notFound().build());
    }

}
