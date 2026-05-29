package com.tripplanning.search;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TripSearchService {

    private final TripSearchCachedReader cachedReader;

    public Page<TripSearchDto> search(String terms, int page, int size) {
        return cachedReader.searchRaw(terms, page, size);
    }
}
