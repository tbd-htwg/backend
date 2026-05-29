package com.tripplanning.social;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import com.tripplanning.social.config.SocialCacheConfig;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CommunityCacheEvictor {

    private final CacheManager cacheManager;

    public void evictForTrip(long tripId) {
        Cache cache = cacheManager.getCache(SocialCacheConfig.COMMUNITY_BUNDLE);
        if (cache != null) {
            cache.evict(tripId);
        }
    }
}
