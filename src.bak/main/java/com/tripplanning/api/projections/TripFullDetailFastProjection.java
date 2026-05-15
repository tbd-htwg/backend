package com.tripplanning.api.projections;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.rest.core.config.Projection;

import com.tripplanning.trip.TripEntity;

/**
 * Trip detail without embedded trip-location image signing; stops use {@link TripLocationLiteProjection}.
 * Signed URLs per stop: {@code GET /api/v2/trips/{id}/trip-location-image-urls}.
 */
@Projection(name = "fullDetailFast", types = { TripEntity.class })
public interface TripFullDetailFastProjection {

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

    List<TripLocationLiteProjection> getTripLocations();

    List<TransportProjection> getTransports();

    List<AccommodationProjection> getAccommodations();
}
