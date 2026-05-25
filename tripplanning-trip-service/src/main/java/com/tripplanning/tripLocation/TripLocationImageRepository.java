package com.tripplanning.tripLocation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tripplanning.images.FeedImagePathRow;

public interface TripLocationImageRepository extends JpaRepository<TripLocationImageEntity, Long> {
    List<TripLocationImageEntity> findByTripLocationId(Long tripLocationId);

    @Query("SELECT t.imagePath FROM TripLocationImageEntity t WHERE t.tripLocation.id = :locationId")
    List<String> findImagePathsByTripLocationId(@Param("locationId") Long locationId);

    @Query(
            """
            select new com.tripplanning.images.FeedImagePathRow(
                tl.trip.id, tl.id, img.id, img.imagePath)
            from TripLocationImageEntity img
            join img.tripLocation tl
            where tl.trip.id in :tripIds
            order by tl.trip.id, tl.id, img.id
            """)
    List<FeedImagePathRow> findFeedImagePathsByTripIds(@Param("tripIds") List<Long> tripIds);
}
