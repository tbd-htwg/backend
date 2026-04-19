package com.tripplanning.comment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(path = "comments", collectionResourceRel = "comments")
public interface CommentRepository extends JpaRepository<CommentEntity, Long> {
    Page<CommentEntity> findByTripIdOrderByCreatedAtDesc(Long tripId, Pageable pageable); // kommentare nach trip id sortiert nach erstellungsdatum absteigend (paginiert)

    void deleteByUserId(Long userId);
}
