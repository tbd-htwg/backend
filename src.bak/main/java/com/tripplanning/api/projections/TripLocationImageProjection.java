package com.tripplanning.api.projections;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.rest.core.config.Projection;

import com.tripplanning.tripLocation.TripLocationImageEntity;

@Projection(name = "withSignedUrl", types = { TripLocationImageEntity.class })
public interface TripLocationImageProjection {
    Long getId();

    @Value("#{@imageService.createSignedReadUrlIfAuthenticated(target.imagePath)}")
    String getSignedReadUrl();
}

