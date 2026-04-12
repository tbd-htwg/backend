package com.tripplanning.tripLocation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TripLocationRepository extends JpaRepository<TripLocationEntity, Long> {

  List<TripLocationEntity> findByTrip_TripId(Long tripId);
}
