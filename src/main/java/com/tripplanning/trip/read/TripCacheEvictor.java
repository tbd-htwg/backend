package com.tripplanning.trip.read;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import com.tripplanning.config.CacheConfig;

import lombok.RequiredArgsConstructor;

/**
 * Single point that knows how to evict the trip-feed/trip-detail caches.
 *
 * <p>Spring Data REST invokes {@code @RepositoryEventHandler} methods through reflection on the
 * raw bean instance, which bypasses Spring AOP proxies. That means {@code @CacheEvict} placed on a
 * handler method does not fire. Wrapping the eviction logic in this collaborator and calling it
 * from event handlers / controllers gives reliable cache invalidation through plain method calls.
 *
 * <p>This evictor only invalidates the local Caffeine instance. On Cloud Run with several backend
 * containers, peer instances catch up via the 10 s {@code expireAfterWrite} window in
 * {@link CacheConfig}.
 */
@Component
@RequiredArgsConstructor
public class TripCacheEvictor {

    private final CacheManager cacheManager;

    /** Evict every paginated feed (whole list, by-user, liked-by). */
    public void evictAllFeeds() {
        clear(CacheConfig.TRIP_FEED_PAGE);
        clear(CacheConfig.TRIP_FEED_BY_USER);
        clear(CacheConfig.TRIP_FEED_LIKED_BY);
    }

    /** Evict the {@link CacheConfig#TRIP_FEED_LIKED_BY} cache (for like add/remove). */
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

    /** Trip CRUD: evict the trip's detail, the existence check, and every feed. */
    public void evictForTripChange(Long tripId) {
        evictTripDetail(tripId);
        evictTripExists(tripId);
        evictAllFeeds();
    }

    private void clear(String name) {
        Cache cache = cacheManager.getCache(name);
        if (cache != null) {
            cache.clear();
        }
    }
}
