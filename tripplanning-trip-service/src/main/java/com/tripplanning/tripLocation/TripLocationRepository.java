package com.tripplanning.tripLocation;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(path = "trip-locations", collectionResourceRel = "trip-locations")
public interface TripLocationRepository extends JpaRepository<TripLocationEntity, Long> {
    Page<TripLocationEntity> findByTripId(Long tripId, Pageable pageable); // locations nach trip id sortiert nach erstellungsdatum absteigend (paginiert)

    @Query(
            "select tl from TripLocationEntity tl join fetch tl.trip t join fetch t.user where tl.id = :id")
    Optional<TripLocationEntity> findByIdWithTripAndUser(@Param("id") Long id);

    @Query(
            "select distinct tl from TripLocationEntity tl left join fetch tl.images where tl.trip.id = :tripId")
    List<TripLocationEntity> findAllByTripIdWithImages(@Param("tripId") Long tripId);
}
