package com.tripplanning.trip;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tripplanning.common.client.SocialServiceClient;

import lombok.RequiredArgsConstructor;

/**
 * Orchestrates liked-trip listing: trip IDs from social-service, entities from Cloud SQL.
 */
@RestController
@RequiredArgsConstructor
public class TripLikedTripsController {

    private final SocialServiceClient socialServiceClient;
    private final TripRepository tripRepository;

    @GetMapping("/api/v2/trips/search/findByLikedByUsersId")
    public Page<TripEntity> findByLikedByUsersId(
            @RequestParam Long userId, Pageable pageable) {
        List<Long> tripIds = socialServiceClient.getLikedTripIdsForUser(userId);
        if (tripIds.isEmpty()) {
            return Page.empty(pageable);
        }
        List<TripEntity> trips = tripRepository.findAllById(tripIds);
        return new PageImpl<>(trips, pageable, trips.size());
    }

    @GetMapping("/api/v2/users/{userId}/likedTrips")
    public Page<TripEntity> getLikedTrips(@PathVariable Long userId, Pageable pageable) {
        return findByLikedByUsersId(userId, pageable);
    }
}
