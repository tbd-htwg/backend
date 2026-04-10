package com.tripplanning.comment;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<CommentEntity, Long> {
    List<CommentEntity> findByTripIdOrderByCreatedAtDesc(Long trip_id); // mehrere Suchergebnisse
    void deleteByAuthorId(Long user_id);
}
