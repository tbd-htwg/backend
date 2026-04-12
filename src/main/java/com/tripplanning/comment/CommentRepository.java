package com.tripplanning.comment;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<CommentEntity, Long> {

  List<CommentEntity> findByTrip_TripIdOrderByCreatedAtDesc(Long tripId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM CommentEntity c WHERE c.user.userId = :userId")
  void deleteByUser_UserId(@Param("userId") Long userId);
}
