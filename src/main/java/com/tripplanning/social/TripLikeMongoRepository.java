package com.tripplanning.social;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(exported = false)
public interface TripLikeMongoRepository extends MongoRepository<TripLikeDocument, String> {
    long countByTripId(Long tripId);
    boolean existsByUserIdAndTripId(Long userId, Long tripId);
    void deleteByUserIdAndTripId(Long userId, Long tripId);
    Page<TripLikeDocument> findByUserId(Long userId, Pageable pageable);
}
