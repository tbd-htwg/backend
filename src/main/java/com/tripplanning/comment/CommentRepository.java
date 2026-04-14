package com.tripplanning.comment;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
@RepositoryRestResource(path = "comments", collectionResourceRel = "comments")
public interface CommentRepository extends JpaRepository<CommentEntity, Long> {
    List<CommentEntity> findByTripIdOrderByCreatedAtDesc(Long tripId); // mehrere Suchergebnisse
    void deleteByUserId(Long userId);
}
