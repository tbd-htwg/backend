package com.tripplanning.social;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CommentRepository extends FirestoreReactiveRepository<CommentDocument> {
    /**
     * Equality-only query (uses Firestore single-field index). Sort newest-first in the service layer
     * so we do not require a composite index on {@code tripId + createdAt}.
     */
    Flux<CommentDocument> findByTripId(Long tripId);

    Mono<Void> deleteByUserId(Long userId);
}
