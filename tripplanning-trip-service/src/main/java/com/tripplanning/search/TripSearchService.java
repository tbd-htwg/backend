package com.tripplanning.search;

import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.tripplanning.trip.read.TripAccessHelper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TripSearchService {

    private final TripSearchCachedReader cachedReader;

    public Page<TripSearchDto> search(String terms, int page, int size, Authentication authentication) {
        TripAccessHelper.requirePublicTripAccessOrAuth(authentication);
        return cachedReader.searchRaw(terms, page, size);
    }
}
