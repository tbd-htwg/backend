package com.tripplanning.search;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Gates readiness until the shared Elasticsearch index matches PostgreSQL on cold start. Pods
 * joining an already-populated cluster become ready quickly.
 */
@Component("searchIndex")
@ConditionalOnProperty(
        name = "tripplanning.search.readiness-enabled",
        havingValue = "true",
        matchIfMissing = true)
@RequiredArgsConstructor
public class SearchIndexHealthIndicator implements HealthIndicator {

    private final SearchIndexCoordinationService coordinationService;

    @Override
    public Health health() {
        SearchIndexStatus status = coordinationService.currentStatus();
        Health.Builder builder =
                status.isReady() ? Health.up() : Health.outOfService().withDetail("reason", status.message());
        String lockOwner = status.lockOwner() != null ? status.lockOwner() : "none";
        return builder
                .withDetail("state", status.state())
                .withDetail("databaseTrips", status.databaseTrips())
                .withDetail("indexedTrips", status.indexedTrips())
                .withDetail("indexingInProgress", status.indexingInProgress())
                .withDetail("lockOwner", lockOwner)
                .build();
    }
}
