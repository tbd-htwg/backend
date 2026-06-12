package com.tripplanning.social;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import com.tripplanning.common.tenant.TenantCacheKeyPrefix;
import com.tripplanning.social.config.SocialCacheConfig;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CommunityCacheEvictor {

    private final CacheManager cacheManager;
    private final TenantCacheKeyPrefix tenantCacheKeyPrefix;

    public void evictForTrip(long tripId) {
        Cache cache = cacheManager.getCache(SocialCacheConfig.COMMUNITY_BUNDLE);
        if (cache != null) {
            cache.evict(tenantCacheKeyPrefix.qualifyTrip(tripId));
        }
    }
}
