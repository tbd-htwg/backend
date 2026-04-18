package com.tripplanning.search;

import org.hibernate.search.mapper.orm.mapping.HibernateOrmMappingConfigurationContext;
import org.hibernate.search.mapper.orm.mapping.HibernateOrmSearchMappingConfigurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.tripplanning.trip.TripEntity;

/**
 * Sets the Hibernate Search logical index name from configuration so each deployment (e.g. develop vs
 * staging) can use a distinct Elasticsearch index when sharing one cluster.
 */
@Component
public class TripSearchMappingConfigurer implements HibernateOrmSearchMappingConfigurer {

    private final String elasticsearchIndexName;

    public TripSearchMappingConfigurer(
            @Value("${tripplanning.search.elasticsearch-index-name:tripentity}") String elasticsearchIndexName) {
        this.elasticsearchIndexName = elasticsearchIndexName;
    }

    @Override
    public void configure(HibernateOrmMappingConfigurationContext context) {
        context.programmaticMapping()
                .type(TripEntity.class)
                .indexed()
                .index(elasticsearchIndexName);
    }
}
