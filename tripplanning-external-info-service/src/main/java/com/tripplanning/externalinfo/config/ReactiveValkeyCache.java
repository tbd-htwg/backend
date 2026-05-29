package com.tripplanning.externalinfo.config;

import java.time.Duration;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Shared Valkey cache for reactive external API results. When {@link ReactiveStringRedisTemplate} is
 * not configured (local dev without Redis), loads through without caching.
 */
@Component
@Slf4j
public class ReactiveValkeyCache {

    private static final String KEY_PREFIX = "extinfo:v1:";

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public ReactiveValkeyCache(
            ObjectMapper objectMapper,
            @Autowired(required = false) ReactiveStringRedisTemplate redisTemplate) {
        this.objectMapper = objectMapper;
        this.redis = redisTemplate;
    }

    public boolean isEnabled() {
        return redis != null;
    }

    public <T> Mono<T> getOrLoad(
            String namespace, String key, Class<T> type, Duration ttl, Supplier<Mono<T>> loader) {
        if (redis == null) {
            return Mono.defer(loader);
        }
        String redisKey = redisKey(namespace, key);
        return redis.opsForValue()
                .get(redisKey)
                .flatMap(json -> deserialize(json, type))
                .switchIfEmpty(
                        Mono.defer(loader)
                                .flatMap(
                                        value ->
                                                redis.opsForValue()
                                                        .set(redisKey, serialize(value), ttl)
                                                        .thenReturn(value))
                                .onErrorResume(
                                        e -> {
                                            log.warn(
                                                    "Valkey cache put failed for {}: {}",
                                                    redisKey,
                                                    e.getMessage());
                                            return Mono.defer(loader);
                                        }));
    }

    public <T> Mono<T> getOrLoad(
            String namespace,
            String key,
            TypeReference<T> typeRef,
            Duration ttl,
            Supplier<Mono<T>> loader) {
        if (redis == null) {
            return Mono.defer(loader);
        }
        String redisKey = redisKey(namespace, key);
        return redis.opsForValue()
                .get(redisKey)
                .flatMap(json -> deserialize(json, typeRef))
                .switchIfEmpty(
                        Mono.defer(loader)
                                .flatMap(
                                        value ->
                                                redis.opsForValue()
                                                        .set(redisKey, serialize(value), ttl)
                                                        .thenReturn(value))
                                .onErrorResume(
                                        e -> {
                                            log.warn(
                                                    "Valkey cache put failed for {}: {}",
                                                    redisKey,
                                                    e.getMessage());
                                            return Mono.defer(loader);
                                        }));
    }

    private String redisKey(String namespace, String key) {
        return KEY_PREFIX + namespace + ":" + key;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize cache value", e);
        }
    }

    private <T> Mono<T> deserialize(String json, Class<T> type) {
        try {
            return Mono.just(objectMapper.readValue(json, type));
        } catch (JsonProcessingException e) {
            log.warn("Valkey cache deserialize failed, treating as miss: {}", e.getMessage());
            return Mono.empty();
        }
    }

    private <T> Mono<T> deserialize(String json, TypeReference<T> typeRef) {
        try {
            return Mono.just(objectMapper.readValue(json, typeRef));
        } catch (JsonProcessingException e) {
            log.warn("Valkey cache deserialize failed, treating as miss: {}", e.getMessage());
            return Mono.empty();
        }
    }
}
