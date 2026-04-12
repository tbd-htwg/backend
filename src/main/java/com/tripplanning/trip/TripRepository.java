package com.tripplanning.trip;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TripRepository extends JpaRepository<TripEntity, Long> {

  List<TripEntity> findByUser_UserId(Long userId);

  List<TripEntity> findByLikedByUsers_UserId(Long userId);

  @Query("SELECT COUNT(u) FROM TripEntity t JOIN t.likedByUsers u WHERE t.tripId = :tripId")
  long countLikes(@Param("tripId") Long tripId);
}
