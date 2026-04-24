package com.tripplanning.trip;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(path = "trips", collectionResourceRel = "trips")
public interface TripRepository extends JpaRepository<TripEntity, Long> {
  
  Page<TripEntity> findByUserId(Long userId, Pageable pageable);
  void deleteByUserId(Long userId);
}

