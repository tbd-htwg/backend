package com.tripplanning.search;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class TripSearchController {

    private final TripSearchService tripSearchService;

    @GetMapping("/trips")
    public ResponseEntity<List<TripSearchDto>> searchTrips(@RequestParam(name = "q") String query) {
        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.ok(List.of()); // Leere Liste bei leerer Anfrage
        }
        
        return ResponseEntity.ok(tripSearchService.search(query));
    }
}