package com.tripplanning.trip;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tripplanning.images.TripFeedLocationImagesHelper;

import lombok.RequiredArgsConstructor;

/**
 * Batch signed URLs for trip-location images on the home feed. Keeps {@code projection=list} fast.
 */
@RestController
@RequestMapping("/api/v2/trips")
@RequiredArgsConstructor
public class TripFeedImagesController {

    private static final int MAX_TRIP_IDS = 50;

    private final TripRepository tripRepository;
    private final TripFeedLocationImagesHelper tripFeedLocationImagesHelper;

    @GetMapping("/feed-location-images")
    public Map<Long, List<String>> feedLocationImages(
            @RequestParam(name = "tripId", required = false) List<Long> tripIds) {
        if (tripIds == null || tripIds.isEmpty()) {
            return Map.of();
        }
        List<Long> slice = tripIds.size() > MAX_TRIP_IDS ? tripIds.subList(0, MAX_TRIP_IDS) : tripIds;
        Map<Long, List<String>> out = new LinkedHashMap<>();
        for (TripEntity trip : tripRepository.findAllById(slice)) {
            out.put(trip.getId(), tripFeedLocationImagesHelper.collectLocationImageUrls(trip));
        }
        return out;
    }
}
