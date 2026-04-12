package com.tripplanning.tripLocation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripLocationRepository extends JpaRepository<TripLocationEntity, Long> {
    @Query("SELECT tl FROM TripLocationEntity tl WHERE tl.trip.trip_id = :tripId")
    List<TripLocationEntity> findByTripId(@Param("tripId") Long tripId);
}
