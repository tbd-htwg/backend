package com.tripplanning.trip;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.tripplanning.images.TripFeedLocationImagesHelper;
import com.tripplanning.trip.read.TripFeedDtos.TripLocationImageRead;
import com.tripplanning.tripLocation.TripLocationRepository;

import lombok.RequiredArgsConstructor;

/**
 * Batch signed URLs for trip-location images: flattened feed carousel and per-stop map for trip detail.
 */
@RestController
@RequestMapping("/api/v2/trips")
@RequiredArgsConstructor
public class TripFeedImagesController {

    private static final int MAX_TRIP_IDS = 50;

    private final TripRepository tripRepository;
    private final TripLocationRepository tripLocationRepository;
    private final TripFeedLocationImagesHelper tripFeedLocationImagesHelper;

    @GetMapping("/feed-location-images")
    public Map<Long, List<String>> feedLocationImages(
            @RequestParam(name = "tripId", required = false) List<Long> tripIds,
            @RequestParam(name = "startIndex", required = false) Integer startIndex,
            @RequestParam(name = "perTripLimit", required = false) Integer perTripLimit) {
        if (tripIds == null || tripIds.isEmpty()) {
            return Map.of();
        }
        List<Long> slice = tripIds.size() > MAX_TRIP_IDS ? tripIds.subList(0, MAX_TRIP_IDS) : tripIds;
        if (perTripLimit != null && perTripLimit < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "perTripLimit must be at least 1");
        }
        if (startIndex != null && startIndex < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startIndex must be non-negative");
        }
        return tripFeedLocationImagesHelper.collectFeedLocationImageUrls(slice, startIndex, perTripLimit);
    }

    /**
     * Second-stage signed image metadata for trip detail: keys are trip-location ids, values are image
     * id + signed read URL pairs for that stop (needed for delete after reload).
     */
    @GetMapping("/{tripId}/trip-location-image-urls")
    public Map<Long, List<TripLocationImageRead>> tripLocationImageUrls(@PathVariable Long tripId) {
        if (!tripRepository.existsById(tripId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        }
        return tripFeedLocationImagesHelper.collectSignedImagesByTripLocationId(
                tripLocationRepository.findAllByTripIdWithImages(tripId));
    }
}
