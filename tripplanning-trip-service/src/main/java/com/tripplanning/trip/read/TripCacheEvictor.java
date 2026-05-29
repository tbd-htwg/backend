package com.tripplanning.trip.read;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import com.tripplanning.config.CacheConfig;

import lombok.RequiredArgsConstructor;

/**
 * Evicts trip feed, detail, and existence caches.
 *
 * <p>Spring Data REST invokes {@code @RepositoryEventHandler} methods through reflection on the
 * raw bean instance, which bypasses Spring AOP proxies. Wrapping eviction in this collaborator gives
 * reliable invalidation through plain method calls.
 */
@Component
@RequiredArgsConstructor
public class TripCacheEvictor {

    private final CacheManager cacheManager;

    /** Evict every paginated feed (whole list, by-user, liked-by) and the cached trip total count. */
    public void evictAllFeeds() {
        clear(CacheConfig.TRIP_FEED_PAGE);
        clear(CacheConfig.TRIP_FEED_BY_USER);
        clear(CacheConfig.TRIP_FEED_LIKED_BY);
        clear(CacheConfig.TRIP_TOTAL_COUNT);
    }

    /** Evict the liked-by feed cache (for like add/remove). */
    public void evictLikedByFeeds() {
        clear(CacheConfig.TRIP_FEED_LIKED_BY);
    }

    /** Evict a single trip's detail entry. */
    public void evictTripDetail(Long tripId) {
        Cache cache = cacheManager.getCache(CacheConfig.TRIP_DETAIL);
        if (cache != null && tripId != null) {
            cache.evict(tripId);
        }
    }

    /** Evict the existence-check entry (used after a trip is created or deleted). */
    public void evictTripExists(Long tripId) {
        Cache cache = cacheManager.getCache(CacheConfig.TRIP_EXISTS);
        if (cache != null && tripId != null) {
            cache.evict(tripId);
        }
    }

    /** Trip CRUD: evict the trip's detail, existence check, total count, and every feed page. */
    public void evictForTripChange(Long tripId) {
        evictTripDetail(tripId);
        evictTripExists(tripId);
        evictAllFeeds();
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

    private void clear(String name) {
        Cache cache = cacheManager.getCache(name);
        if (cache != null) {
            cache.clear();
        }
    }
}
