package com.tripplanning.tripLocation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TripLocationImageRepository extends JpaRepository<TripLocationImageEntity, Long> {
    List<TripLocationImageEntity> findByTripLocationId(Long tripLocationId);
}
