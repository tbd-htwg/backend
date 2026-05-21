package com.tripplanning.externalinfo.ApiProxyServices;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.tripplanning.externalinfo.dto.ExternalInfoDtos.PlaceDetailsResult;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class CachedGooglePlacesService {

    private final GooglePlacesApi googlePlacesApi;

    @Cacheable(value = "places", key = "#placeId")
    public Mono<PlaceDetailsResult> getPlaceDetailsCached(String placeId) {
        return googlePlacesApi.fetchPlaceDetailsUncached(placeId);
    }
}
