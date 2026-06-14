package com.tripplanning.search;

import org.hibernate.search.mapper.orm.mapping.HibernateOrmMappingConfigurationContext;
import org.hibernate.search.mapper.orm.mapping.HibernateOrmSearchMappingConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.tripplanning.trip.TripEntity;

/**
 * Per-tenant Hibernate Search mapping for Standard/Enterprise pools where datasource routing is
 * enabled. Physical index names are resolved by {@link TenantIndexLayoutStrategy}.
 */
@Component
@ConditionalOnProperty(name = "tripplanning.tenant.datasource-routing.enabled", havingValue = "true")
public class TenantAwareTripSearchMappingConfigurer implements HibernateOrmSearchMappingConfigurer {

  @Override
  public void configure(HibernateOrmMappingConfigurationContext context) {
    context.programmaticMapping().type(TripEntity.class).indexed().index("tripentity");
  }
}
