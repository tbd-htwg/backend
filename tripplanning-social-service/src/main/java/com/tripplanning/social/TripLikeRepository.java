package com.tripplanning.social;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TripLikeRepository extends FirestoreReactiveRepository<TripLikeDocument> {
    /**
     * Equality on {@code tripId}. Public like counts use {@link FirestoreSocialService} aggregation
     * queries; this stream remains for any code paths that need actual like documents.
     */
    Flux<TripLikeDocument> findByTripId(Long tripId);

    Mono<TripLikeDocument> findByUserIdAndTripId(Long userId, Long tripId);
    Mono<Void> deleteByUserIdAndTripId(Long userId, Long tripId);
    Flux<TripLikeDocument> findByUserId(Long userId);
}
