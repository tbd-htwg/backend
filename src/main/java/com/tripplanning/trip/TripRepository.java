package com.tripplanning.trip;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TripRepository extends JpaRepository<TripEntity, Long> {
  @Query("SELECT t FROM TripEntity t WHERE t.user.user_id = :userId")
  List<TripEntity> findByUserId(@Param("userId") Long userId);

  @Modifying
  @Query("DELETE FROM TripEntity t WHERE t.user.user_id = :userId")
  void deleteByUserId(@Param("userId") Long userId);

  @Query("SELECT t FROM TripEntity t JOIN t.likedByUsers u WHERE u.user_id = :userId")
  List<TripEntity> findByLikedByUsersId(@Param("userId") Long userId);

  @Query("SELECT size(t.likedByUsers) FROM TripEntity t WHERE t.trip_id = :tripId")
    int countLikes(@Param("tripId") Long tripId); // zählt likes direkt in DB
}

