package com.tripplanning.search;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class TripSearchController {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private final TripSearchService tripSearchService;
    private final EntitySearchSuggestService entitySearchSuggestService;

    @GetMapping("/trips")
    public ResponseEntity<Page<TripSearchDto>> searchTrips(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "" + DEFAULT_SIZE) int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_SIZE);
        return ResponseEntity.ok(tripSearchService.search(query, safePage, safeSize));
    }

    @GetMapping("/suggest/transport")
    public List<SearchSuggestionDto> suggestTransport(
            @RequestParam(name = "prefix", required = false) String prefix) {
        return entitySearchSuggestService.suggestTransport(prefix);
    }

    @GetMapping("/suggest/location")
    public List<SearchSuggestionDto> suggestLocation(
            @RequestParam(name = "prefix", required = false) String prefix) {
        return entitySearchSuggestService.suggestLocation(prefix);
    }

    @GetMapping("/suggest/accommodation")
    public List<SearchSuggestionDto> suggestAccommodation(
            @RequestParam(name = "prefix", required = false) String prefix) {
        return entitySearchSuggestService.suggestAccommodation(prefix);
    }

    /**
     * Aggregate facet suggestions from transport, location, and accommodation indices in one
     * response. Bundle mode (no {@code scope}) requires a prefix of at least two characters.
     */
    @GetMapping("/suggest/filters")
    public FacetFilterSuggestResponse suggestFilters(
            @RequestParam(name = "prefix", required = false) String prefix,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "scope", required = false) String scope) {
        return entitySearchSuggestService.suggestFilters(prefix, limit, scope);
    }
}