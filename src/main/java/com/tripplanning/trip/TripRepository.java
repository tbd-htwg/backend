package com.tripplanning.trip;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(path = "trips", collectionResourceRel = "trips")
public interface TripRepository extends JpaRepository<TripEntity, Long> {
  Page<TripEntity> findByUserId(Long userId, Pageable pageable);
  void deleteByUserId(Long userId);
  Page<TripEntity> findByLikedByUsersId(Long userId, Pageable pageable);
  @Query("SELECT COUNT(u) FROM TripEntity t LEFT JOIN t.likedByUsers u WHERE t.id = :tripId")
  int countLikes(@Param("tripId") Long tripId); // counts likes; returns 0 when trip has no likes or no row
}

