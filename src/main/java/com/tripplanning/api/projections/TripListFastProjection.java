package com.tripplanning.api.projections;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.rest.core.config.Projection;

import com.tripplanning.trip.TripEntity;

/**
 * Feed/list shape without GCS signing (for load tests and clients that do not need avatars inline).
 */
@Projection(name = "listFast", types = { TripEntity.class })
public interface TripListFastProjection {

    Long getId();

    String getTitle();

    String getDestination();

    LocalDate getStartDate();

    String getShortDescription();

    @Value("#{target.user.id}")
    Long getAuthorId();

    @Value("#{target.user.name}")
    String getAuthorName();

    @Value("#{target.locationNames}")
    List<String> getLocations();

    @Value("#{target.accommodationNames}")
    List<String> getAccommodationNames();

    @Value("#{target.transportTypes}")
    List<String> getTransportTypes();
}
