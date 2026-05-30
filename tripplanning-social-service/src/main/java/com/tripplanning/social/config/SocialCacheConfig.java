package com.tripplanning.social.config;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.github.benmanes.caffeine.cache.Caffeine;

@Configuration
@EnableCaching
public class SocialCacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(SocialCacheConfig.class);

    @Value("${tripplanning.cache.redis-ttl-seconds:30}")
    private int cacheTtlSeconds;

    private static final String CACHE_GEN = "v1";

    public static final String COMMUNITY_BUNDLE = "communityBundle" + CACHE_GEN;

    private static final String[] CACHE_NAMES = {COMMUNITY_BUNDLE};

    @Override
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn(
                        "Redis cache get failed for {} (key={}): {}",
                        cache.getName(),
                        key,
                        exception.getMessage());
                try {
                    cache.evict(key);
                } catch (RuntimeException evictFailed) {
                    log.warn("Failed to evict corrupt cache entry: {}", evictFailed.getMessage());
                }
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn(
                        "Redis cache put failed for {} (key={}): {}",
                        cache.getName(),
                        key,
                        exception.getMessage());
            }
        };
    }

    @Bean
    @ConditionalOnProperty(name = "spring.data.redis.host")
    CacheManager redisCacheManager(
            RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        GenericJackson2JsonRedisSerializer valueSerializer = cacheValueSerializer(objectMapper);
        RedisCacheConfiguration defaults =
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofSeconds(cacheTtlSeconds))
                        .disableCachingNullValues()
                        .serializeValuesWith(
                                RedisSerializationContext.SerializationPair.fromSerializer(
                                        valueSerializer));
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
                        .expireAfterWrite(Duration.ofSeconds(cacheTtlSeconds))
                        .maximumSize(10_000));
        return manager;
    }

    static GenericJackson2JsonRedisSerializer cacheValueSerializer(ObjectMapper objectMapper) {
        ObjectMapper redisMapper = objectMapper.copy();
        var typeValidator =
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.tripplanning")
                        .allowIfSubType("java.util")
                        .allowIfSubType("java.lang")
                        .build();
        redisMapper.activateDefaultTyping(
                typeValidator, ObjectMapper.DefaultTyping.EVERYTHING, JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(redisMapper);
    }
}
