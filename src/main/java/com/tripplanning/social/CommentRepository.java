package com.tripplanning.social;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CommentRepository extends FirestoreReactiveRepository<CommentDocument> {
    /**
     * Equality on {@code tripId} (e.g. deletes). Paginated reads use {@link FirestoreSocialService}
     * with a composite index on {@code tripId + createdAt + __name__}.
     */
    Flux<CommentDocument> findByTripId(Long tripId);

    Mono<Void> deleteByUserId(Long userId);
}
