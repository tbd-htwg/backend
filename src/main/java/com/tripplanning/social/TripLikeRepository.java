package com.tripplanning.social;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TripLikeRepository extends FirestoreReactiveRepository<TripLikeDocument> {
    /**
     * Simple equality query on {@code tripId} (no composite index). Used for like counts instead of
     * derived {@code countByTripId}, which relies on Firestore aggregation and can fail on some emulator/SDK combos.
     */
    Flux<TripLikeDocument> findByTripId(Long tripId);

    Mono<TripLikeDocument> findByUserIdAndTripId(Long userId, Long tripId);
    Mono<Void> deleteByUserIdAndTripId(Long userId, Long tripId);
    Flux<TripLikeDocument> findByUserId(Long userId);
}
