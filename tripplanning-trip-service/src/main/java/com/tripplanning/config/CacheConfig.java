package com.tripplanning.config;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import com.github.benmanes.caffeine.cache.Caffeine;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String TRIP_FEED_PAGE = "tripFeedPage";
    public static final String TRIP_FEED_BY_USER = "tripFeedByUser";
    public static final String TRIP_FEED_LIKED_BY = "tripFeedLikedBy";
    public static final String TRIP_DETAIL = "tripDetail";
    public static final String TRIP_EXISTS = "tripExists";

    private static final String[] CACHE_NAMES = {
        TRIP_FEED_PAGE,
        TRIP_FEED_BY_USER,
        TRIP_FEED_LIKED_BY,
        TRIP_DETAIL,
        TRIP_EXISTS
    };

    @Bean
    @ConditionalOnProperty(name = "spring.data.redis.host")
    @ConditionalOnBean(RedisConnectionFactory.class)
    CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaults =
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofSeconds(10))
                        .disableCachingNullValues();
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .initialCacheNames(java.util.Set.of(CACHE_NAMES))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    CacheManager caffeineCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(CACHE_NAMES);
        manager.setCaffeine(
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofSeconds(10))
                        .maximumSize(10_000));
        return manager;
    }
}
