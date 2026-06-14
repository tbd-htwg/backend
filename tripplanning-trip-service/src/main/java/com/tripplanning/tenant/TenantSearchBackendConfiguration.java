package com.tripplanning.tenant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "tripplanning.tenant.datasource-routing.enabled", havingValue = "true")
public class TenantSearchBackendConfiguration {

  @Bean
  public org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer tenantSearchLayoutCustomizer() {
    return hibernateProperties ->
        hibernateProperties.put(
            "hibernate.search.backend.layout.strategy", "tenantIndexLayoutStrategy");
  }
}
