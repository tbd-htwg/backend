package com.tripplanning.api.projections;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.rest.core.config.Projection;
import org.springframework.beans.factory.annotation.Value;

import com.tripplanning.tripLocation.TripLocationEntity;

@Projection(name = "withImages", types = { TripLocationEntity.class })
public interface TripLocationProjection {
    
    Long getId();

    @Value("#{target.trip.id}")
    Long getTripId();
    
    @Value("#{target.location.id}")
    Long getLocationId();

    @Value("#{target.location.name}")
    String getLocationName();
    
    String getDescription();
    
    @Value("#{target.startDate}")
    LocalDateTime getStartDate();
    
    @Value("#{target.endDate}")
    LocalDateTime getEndDate();

    List<TripLocationImageProjection> getImages();
}