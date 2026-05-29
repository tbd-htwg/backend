package com.tripplanning.social;

import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tripplanning.common.client.TripServiceClient;

import lombok.RequiredArgsConstructor;

/** Short-lived cache for trip-service HEAD /internal/trips/{id} (hot on community reads). */
@Component
@RequiredArgsConstructor
public class TripExistenceCache {

    private static final int TTL_SECONDS = 60;

    private final TripServiceClient tripServiceClient;
    private final Cache<Long, Boolean> cache =
            Caffeine.newBuilder().expireAfterWrite(TTL_SECONDS, TimeUnit.SECONDS).maximumSize(50_000).build();

    public boolean tripExists(long tripId) {
        return cache.get(tripId, tripServiceClient::tripExists);
    }
}
