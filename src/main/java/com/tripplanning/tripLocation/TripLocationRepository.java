package com.tripplanning.tripLocation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(path = "trip-locations", collectionResourceRel = "trip-locations")
public interface TripLocationRepository extends JpaRepository<TripLocationEntity, Long> {
    Page<TripLocationEntity> findByTripId(Long tripId, Pageable pageable); // locations nach trip id sortiert nach erstellungsdatum absteigend (paginiert)
}
