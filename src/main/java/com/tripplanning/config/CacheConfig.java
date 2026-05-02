package com.tripplanning.config;

import java.time.Duration;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * In-process cache for the hot read paths (trip feed, trip detail, trip-existence checks).
 *
 * <p>Caffeine is per-JVM, so on Cloud Run with several concurrent backend instances a write
 * (POST/PATCH/DELETE) routed through instance A only evicts that instance's local entries. The
 * 10-second {@code expireAfterWrite} bounds worst-case staleness on the other instances; for a
 * public feed and trip detail this is acceptable. If we ever need stronger consistency we can
 * swap this {@link CacheManager} bean for a Redis/Memorystore-backed manager without touching
 * the {@code @Cacheable} / {@code @CacheEvict} annotations on services and write handlers.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String TRIP_FEED_PAGE = "tripFeedPage";
    public static final String TRIP_FEED_BY_USER = "tripFeedByUser";
    public static final String TRIP_FEED_LIKED_BY = "tripFeedLikedBy";
    public static final String TRIP_DETAIL = "tripDetail";
    public static final String TRIP_EXISTS = "tripExists";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager =
                new CaffeineCacheManager(
                        TRIP_FEED_PAGE,
                        TRIP_FEED_BY_USER,
                        TRIP_FEED_LIKED_BY,
                        TRIP_DETAIL,
                        TRIP_EXISTS);
        manager.setCaffeine(
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofSeconds(10))
                        .maximumSize(10_000));
        return manager;
    }
}
