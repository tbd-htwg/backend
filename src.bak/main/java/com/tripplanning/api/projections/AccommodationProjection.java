package com.tripplanning.api.projections;

import org.springframework.data.rest.core.config.Projection;

import com.tripplanning.accommodation.AccomEntity;

@Projection(name = "tripDetail", types = { AccomEntity.class })
public interface AccommodationProjection {
    long getId();
    String getType();
    String getName();
    String getAddress();
}

