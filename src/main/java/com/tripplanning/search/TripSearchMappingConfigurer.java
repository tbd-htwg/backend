package com.tripplanning.search;

import org.hibernate.search.mapper.orm.mapping.HibernateOrmMappingConfigurationContext;
import org.hibernate.search.mapper.orm.mapping.HibernateOrmSearchMappingConfigurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.tripplanning.accommodation.AccomEntity;
import com.tripplanning.location.LocationEntity;
import com.tripplanning.transport.TransportEntity;
import com.tripplanning.trip.TripEntity;

/**
 * Sets Hibernate Search logical index names from configuration so each deployment (e.g. develop vs
 * staging) can use distinct Elasticsearch indices when sharing one cluster.
 */
@Component
public class TripSearchMappingConfigurer implements HibernateOrmSearchMappingConfigurer {

    private final String tripIndexName;
    private final String locationIndexName;
    private final String transportIndexName;
    private final String accommodationIndexName;

    public TripSearchMappingConfigurer(
            @Value("${tripplanning.search.elasticsearch-index-name:tripentity}") String tripIndexName,
            @Value("${tripplanning.search.elasticsearch-index-location:locationentity}")
                    String locationIndexName,
            @Value("${tripplanning.search.elasticsearch-index-transport:transportentity}")
                    String transportIndexName,
            @Value("${tripplanning.search.elasticsearch-index-accommodation:accommodationentity}")
                    String accommodationIndexName) {
        this.tripIndexName = tripIndexName;
        this.locationIndexName = locationIndexName;
        this.transportIndexName = transportIndexName;
        this.accommodationIndexName = accommodationIndexName;
    }

    @Override
    public void configure(HibernateOrmMappingConfigurationContext context) {
        var mapping = context.programmaticMapping();
        mapping.type(TripEntity.class).indexed().index(tripIndexName);
        mapping.type(LocationEntity.class).indexed().index(locationIndexName);
        mapping.type(TransportEntity.class).indexed().index(transportIndexName);
        mapping.type(AccomEntity.class).indexed().index(accommodationIndexName);
    }
}
