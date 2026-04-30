package com.tripplanning.api.projections;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.rest.core.config.Projection;
import org.springframework.beans.factory.annotation.Value;

import com.tripplanning.trip.TripEntity;


@Projection(name = "fullDetail", types = { TripEntity.class })
public interface TripFullDetailProjection {
    Long getId();
    String getTitle();
    String getDestination();
    LocalDate getStartDate();
    String getShortDescription();
    String getLongDescription();

    @Value("#{target.user.id}")
    Long getAuthorId();
    
    @Value("#{target.user.name}")
    String getAuthorName();

    @Value("#{@imageService.createSignedReadUrlIfAuthenticated(target.user.imagePath)}")
    String getAuthorProfileImageUrl();

    List<TripLocationProjection> getTripLocations();

    List<TransportProjection> getTransports();

    List<AccommodationProjection> getAccommodations();
}

