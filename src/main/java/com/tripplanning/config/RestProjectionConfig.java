package com.tripplanning.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.rest.core.config.ProjectionDefinitionConfiguration;
import org.springframework.data.rest.core.config.RepositoryRestConfiguration;
import org.springframework.data.rest.webmvc.config.RepositoryRestConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import com.tripplanning.api.projections.AccommodationProjection;
import com.tripplanning.api.projections.PublicUserProfileProjection;
import com.tripplanning.api.projections.TransportProjection;
import com.tripplanning.api.projections.TripFullDetailProjection;
import com.tripplanning.api.projections.TripListProjection;
import com.tripplanning.api.projections.TripLocationImageProjection;
import com.tripplanning.api.projections.TripLocationLiteProjection;
import com.tripplanning.api.projections.TripLocationProjection;

@Configuration
public class RestProjectionConfig implements RepositoryRestConfigurer {

    @Override
    public void configureRepositoryRestConfiguration(
            RepositoryRestConfiguration config,
            CorsRegistry cors) {
        ProjectionDefinitionConfiguration projections = config.getProjectionConfiguration();
        projections.addProjection(TripListProjection.class);
        projections.addProjection(TripFullDetailProjection.class);
        projections.addProjection(TripLocationProjection.class);
        projections.addProjection(TripLocationLiteProjection.class);
        projections.addProjection(TripLocationImageProjection.class);
        projections.addProjection(PublicUserProfileProjection.class);
        projections.addProjection(TransportProjection.class);
        projections.addProjection(AccommodationProjection.class);
    }
}

