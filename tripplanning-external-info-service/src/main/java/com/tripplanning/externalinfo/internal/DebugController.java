package com.tripplanning.externalinfo.internal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripplanning.externalinfo.config.ReactiveValkeyCache;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/internal/debug")
@RequiredArgsConstructor
public class DebugController {

    private final ReactiveValkeyCache valkeyCache;

    @Autowired(required = false)
    private ReactiveStringRedisTemplate redisTemplate;

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
        body.put("cacheBackend", valkeyCache.isEnabled() ? "valkey" : "none");
        if (valkeyCache.isEnabled() && redisTemplate != null) {
            try {
                Long keyCount =
                        redisTemplate
                                .getConnectionFactory()
                                .getReactiveConnection()
                                .serverCommands()
                                .dbSize()
                                .block();
                body.put("keyCount", keyCount);
            } catch (Exception e) {
                body.put("keyCountError", e.getMessage());
            }
        }
        body.put(
                "sampleKeyPatterns",
                List.of(
                        "extinfo:v1:places:*",
                        "extinfo:v1:weather:*",
                        "extinfo:v1:warnings:*",
                        "extinfo:v1:tours:*"));
        return body;
    }
}
