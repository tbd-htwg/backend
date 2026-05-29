package com.tripplanning.trip.read;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import com.tripplanning.config.CacheConfig;

import lombok.RequiredArgsConstructor;

/**
 * Evicts the trip-detail Valkey/Caffeine cache.
 *
 * <p>Spring Data REST invokes {@code @RepositoryEventHandler} methods through reflection on the
 * raw bean instance, which bypasses Spring AOP proxies. That means {@code @CacheEvict} placed on a
 * handler method does not fire. Wrapping the eviction logic in this collaborator and calling it
 * from event handlers / controllers gives reliable cache invalidation through plain method calls.
 */
@Component
@RequiredArgsConstructor
public class TripCacheEvictor {

    private final CacheManager cacheManager;

    /** Evict a single trip's detail entry. */
    public void evictTripDetail(Long tripId) {
        Cache cache = cacheManager.getCache(CacheConfig.TRIP_DETAIL);
        if (cache != null && tripId != null) {
            cache.evict(tripId);
        }
    }

    /** Trip CRUD / nested trip mutations: evict that trip's cached detail payload. */
    public void evictForTripChange(Long tripId) {
        evictTripDetail(tripId);
    }

    /**
     * Shared catalog mutations (accommodation/transport lookups) may affect many trip details; clear
     * the whole detail cache rather than tracking reverse references.
     */
    public void evictAllTripDetails() {
        Cache cache = cacheManager.getCache(CacheConfig.TRIP_DETAIL);
        if (cache != null) {
            cache.clear();
        }
    }

    /** Kept for social-service hook; liked-by feed is no longer cached. */
    public void evictLikedByFeeds() {}

    /** Kept for call sites that previously cleared feed caches. */
    public void evictAllFeeds() {
        evictAllTripDetails();
    }
}
