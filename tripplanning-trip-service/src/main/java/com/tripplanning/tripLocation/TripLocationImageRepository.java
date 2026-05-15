package com.tripplanning.tripLocation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripLocationImageRepository extends JpaRepository<TripLocationImageEntity, Long> {
    List<TripLocationImageEntity> findByTripLocationId(Long tripLocationId);
    
    @Query("SELECT t.imagePath FROM TripLocationImageEntity t WHERE t.tripLocation.id = :locationId")
    List<String> findImagePathsByTripLocationId(@Param("locationId") Long locationId);
}
