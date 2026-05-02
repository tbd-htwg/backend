package com.tripplanning.social;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CommentRepository extends FirestoreReactiveRepository<CommentDocument> {
    Flux<CommentDocument> findByTripIdOrderByCreatedAtDesc(Long tripId);
    Mono<Void> deleteByUserId(Long userId);
}
