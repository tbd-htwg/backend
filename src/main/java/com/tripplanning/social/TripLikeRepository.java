package com.tripplanning.social;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TripLikeRepository extends FirestoreReactiveRepository<TripLikeDocument> {
    Mono<Long> countByTripId(Long tripId);
    Mono<TripLikeDocument> findByUserIdAndTripId(Long userId, Long tripId);
    Mono<Void> deleteByUserIdAndTripId(Long userId, Long tripId);
    Flux<TripLikeDocument> findByUserId(Long userId);
}
