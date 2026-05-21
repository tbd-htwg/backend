package com.tripplanning.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripplanning.search.SearchIndexCoordinationService;
import com.tripplanning.search.SearchIndexStatus;

import lombok.RequiredArgsConstructor;

/** Operational debug JSON for Redis caches and the Hibernate Search index (not for production traffic). */
@RestController
@RequestMapping("/internal/debug")
@RequiredArgsConstructor
public class DebugController {

    private final SearchIndexCoordinationService searchIndexCoordination;
    private final CacheManager cacheManager;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired(required = false)
    private RedisConnectionFactory redisConnectionFactory;

    @GetMapping("/search-index")
    public ResponseEntity<SearchIndexStatus> searchIndex() {
        return ResponseEntity.ok(searchIndexCoordination.currentStatus());
    }

    @GetMapping("/redis")
    public ResponseEntity<Map<String, Object>> redisOverview() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cacheBackend", redisConnectionFactory != null ? "redis" : "caffeine");
        body.put("springCaches", describeSpringCaches());
        if (redisTemplate != null) {
            body.put("redis", describeRedisServer(redisTemplate));
        } else {
            body.put("redis", Map.of("connected", false));
        }
        return ResponseEntity.ok(body);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> overview() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("searchIndex", searchIndexCoordination.currentStatus());
        body.put("redis", redisOverview().getBody());
        body.put(
                "links",
                Map.of(
                        "searchIndex", "/internal/debug/search-index",
                        "redis", "/internal/debug/redis",
                        "elasticsearchCat",
                        "Use ingress /debug/elasticsearch/_cat/indices?v for index shards",
                        "redisUi", "Use ingress /debug/redis/ for Redis Commander"));
        return ResponseEntity.ok(body);
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

    private static Map<String, Object> describeRedisServer(StringRedisTemplate template) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("connected", true);
        try {
            Long dbSize = template.getConnectionFactory().getConnection().serverCommands().dbSize();
            info.put("keyCount", dbSize);
        } catch (Exception e) {
            info.put("keyCountError", e.getMessage());
        }
        info.put(
                "sampleKeyPatterns",
                List.of(
                        "tripFeedPage*",
                        "tripDetail*",
                        "tripExists*",
                        "tripplanning:search:index:*"));
        return info;
    }
}
