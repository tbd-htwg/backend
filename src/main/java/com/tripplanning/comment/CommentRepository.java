package com.tripplanning.comment;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<CommentEntity, Long> {
    @Query("SELECT c FROM CommentEntity c WHERE c.trip.trip_id = :tripId ORDER BY c.createdAt DESC")
    List<CommentEntity> findByTripIdOrderByCreatedAtDesc(@Param("tripId") Long tripId);

    @Modifying
    @Query("DELETE FROM CommentEntity c WHERE c.user.user_id = :userId")
    void deleteByAuthorId(@Param("userId") Long userId);
}
