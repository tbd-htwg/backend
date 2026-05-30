package com.tripplanning.externalinfo.ApiProxyServices;

import org.springframework.stereotype.Service;

import com.tripplanning.externalinfo.config.ExternalInfoCacheTtls;
import com.tripplanning.externalinfo.config.ReactiveValkeyCache;
import com.tripplanning.externalinfo.dto.ExternalInfoDtos.PlaceDetailsResult;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class CachedGooglePlacesService {

    private final GooglePlacesApi googlePlacesApi;
    private final ReactiveValkeyCache valkeyCache;

    public Mono<PlaceDetailsResult> getPlaceDetailsCached(String placeId) {
        return valkeyCache.getOrLoad(
                "places",
                placeId,
                PlaceDetailsResult.class,
                ExternalInfoCacheTtls.PLACES,
                () -> googlePlacesApi.fetchPlaceDetailsUncached(placeId));
    }
}
