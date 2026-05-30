package com.tripplanning.search;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final TripSimilarityPort tripSimilarityPort;

    @GetMapping("/trips")
    public ResponseEntity<Page<TripSearchDto>> searchTrips(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "" + DEFAULT_SIZE) int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_SIZE);
        return ResponseEntity.ok(tripSearchService.search(query, safePage, safeSize));
    }

    /**
     * Returns trips similar to the given trip, based on a "More Like This" query.
     * Useful for "You might also like" widgets on trip detail pages.
     *
     * @param id   the reference trip ID
     * @param page zero-based page index
     * @param size page size (capped at {@value MAX_SIZE})
     */
    @GetMapping("/trips/{id}/similar")
    public ResponseEntity<Page<TripSearchDto>> similarTrips(
            @PathVariable Long id,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "" + DEFAULT_SIZE) int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_SIZE);
        List<Long> ref = List.of(id);
        return ResponseEntity.ok(tripSimilarityPort.findSimilar(ref, ref, safePage, safeSize));
    }
}