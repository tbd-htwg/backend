package com.tripplanning.externalinfo.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/internal/debug")
@RequiredArgsConstructor
public class DebugController {

    private final CacheManager cacheManager;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired(required = false)
    private RedisConnectionFactory redisConnectionFactory;

    @GetMapping
    public Mono<Map<String, Object>> overview() {
        return Mono.fromSupplier(this::buildOverview);
    }

    @GetMapping("/redis")
    public Mono<Map<String, Object>> redis() {
        return Mono.fromSupplier(this::buildRedisSection);
    }

    private Map<String, Object> buildOverview() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("redis", buildRedisSection());
        body.put(
                "links",
                Map.of(
                        "redisUi", "/debug/redis/",
                        "elasticsearch", "/debug/elasticsearch/_cat/indices?v"));
        return body;
    }

    private Map<String, Object> buildRedisSection() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cacheBackend", redisConnectionFactory != null ? "redis" : "caffeine");
        body.put("springCaches", describeSpringCaches());
        if (redisTemplate != null) {
            try {
                Long keyCount =
                        redisTemplate
                                .getConnectionFactory()
                                .getConnection()
                                .serverCommands()
                                .dbSize();
                body.put("keyCount", keyCount);
            } catch (Exception e) {
                body.put("keyCountError", e.getMessage());
            }
        }
        body.put(
                "sampleKeyPatterns",
                List.of("places*", "weather*", "warnings*", "tours*", "transportRouteV2*"));
        return body;
    }

    private List<Map<String, Object>> describeSpringCaches() {
        List<Map<String, Object>> caches = new ArrayList<>();
        for (String name : cacheManager.getCacheNames()) {
            Cache cache = cacheManager.getCache(name);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", name);
            entry.put("type", cache != null ? cache.getClass().getSimpleName() : "unknown");
            caches.add(entry);
        }
        return caches;
    }
}
