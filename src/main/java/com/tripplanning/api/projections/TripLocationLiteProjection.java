package com.tripplanning.api.projections;

import java.time.LocalDateTime;

import org.springframework.data.rest.core.config.Projection;
import org.springframework.beans.factory.annotation.Value;

import com.tripplanning.tripLocation.TripLocationEntity;

@Projection(name = "lite", types = { TripLocationEntity.class })
public interface TripLocationLiteProjection {
    
    Long getId();
    String getDescription();
    LocalDateTime getStartDate();
    LocalDateTime getEndDate();

    @Value("#{target.trip.id}")
    Long getTripId();
    
    @Value("#{target.location.id}")
    Long getLocationId();

    @Value("#{target.location.name}")
    String getLocationName();
    
}
