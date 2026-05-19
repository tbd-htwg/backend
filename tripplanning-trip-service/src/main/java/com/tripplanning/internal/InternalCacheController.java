package com.tripplanning.internal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripplanning.trip.read.TripCacheEvictor;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal/cache")
@RequiredArgsConstructor
public class InternalCacheController {

    private final TripCacheEvictor tripCacheEvictor;

    @PostMapping("/trips/liked-by/evict")
    public ResponseEntity<Void> evictLikedByFeeds() {
        tripCacheEvictor.evictLikedByFeeds();
        return ResponseEntity.noContent().build();
    }
}
