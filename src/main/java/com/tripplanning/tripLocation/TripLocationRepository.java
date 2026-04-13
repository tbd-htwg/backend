package com.tripplanning.tripLocation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource
public interface TripLocationRepository extends JpaRepository<TripLocationEntity, Long> {
    List<TripLocationEntity> findByTripId(Long tripId); // Gibt alle Stopps eines Trips
}
