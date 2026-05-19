package com.tripplanning.api.projections;

import org.springframework.data.rest.core.config.Projection;

import com.tripplanning.transport.TransportEntity;

@Projection(name = "tripDetail", types = { TransportEntity.class })
public interface TransportProjection {
    long getId();
    String getType();
}

