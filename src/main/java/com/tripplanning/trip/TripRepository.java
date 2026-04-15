package com.tripplanning.trip;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;

@RepositoryRestResource(path = "trips", collectionResourceRel = "trips")
public interface TripRepository extends JpaRepository<TripEntity, Long> {
  List<TripEntity> findByUserId(Long userId);
  void deleteByUserId(Long userId);
  List<TripEntity> findByLikedByUsersId(Long userId);
  @Query("SELECT size(t.likedByUsers) FROM TripEntity t WHERE t.id = :tripId")
    int countLikes(@Param("tripId") Long tripId); // zählt likes direkt in DB
}

