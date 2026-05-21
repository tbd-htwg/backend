package com.tripplanning.externalinfo.config;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Configuration
@EnableCaching
public class CacheConfig {

    private static final String[] CACHE_NAMES = {
        "places", "warnings", "weather", "tours", "transportDistance"
    };
    private static final Duration PLACES_TTL = Duration.ofDays(7);
    private static final Duration VOLATILE_TTL = Duration.ofDays(1);

    /**
     * {@code @Cacheable} methods return {@code Mono}; they require an async Caffeine cache.
     * Sync {@link org.springframework.data.redis.cache.RedisCacheManager} cannot store reactive
     * publishers and breaks external API calls when Redis is configured.
     */
    @Bean
    CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager =
                new CaffeineCacheManager(CACHE_NAMES) {
                    @Override
                    protected AsyncCache<Object, Object> createAsyncCaffeineCache(String name) {
                        Caffeine<Object, Object> builder =
                                Caffeine.newBuilder()
                                        .maximumSize("places".equals(name) ? 1000 : 500);
                        Duration ttl = "places".equals(name) ? PLACES_TTL : VOLATILE_TTL;
                        return builder.expireAfterWrite(ttl.toMillis(), TimeUnit.MILLISECONDS)
                                .buildAsync();
                    }
                };
        cacheManager.setAsyncCacheMode(true);
        return cacheManager;
    }
}
