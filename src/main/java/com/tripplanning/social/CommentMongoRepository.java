package com.tripplanning.social;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(exported = false)
public interface CommentMongoRepository extends MongoRepository<CommentDocument, String> {
    Page<CommentDocument> findByTripIdOrderByCreatedAtDesc(Long tripId, Pageable pageable);
    void deleteByUserId(Long userId);
}
