package com.tripplanning.images;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.tripplanning.trip.TripEntity;
import com.tripplanning.trip.read.TripFeedDtos.TripLocationImageRead;
import com.tripplanning.tripLocation.TripLocationEntity;
import com.tripplanning.tripLocation.TripLocationImageEntity;
import com.tripplanning.tripLocation.TripLocationImageRepository;

import lombok.RequiredArgsConstructor;

/**
 * Flattens trip-location images into signed GET URLs for feed cards (authenticated requests only).
 */
@Component
@RequiredArgsConstructor
public class TripFeedLocationImagesHelper {

    private final ImageService imageService;
    private final TripLocationImageRepository tripLocationImageRepository;

    /**
     * Batch feed carousel URLs for many trips. Paths are loaded in one query; signing runs in parallel.
     *
     * @param tripIds request order preserved in the response map
     * @param startIndex first flattened image index per trip (0-based); ignored when null
     * @param perTripLimit max images per trip from {@code startIndex}; null means all remaining
     */
    /** Trip ids from {@code tripIds} that have at least one non-blank location image path. */
    public Set<Long> tripIdsWithLocationImages(List<Long> tripIds) {
        if (tripIds == null || tripIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(tripLocationImageRepository.findTripIdsWithLocationImages(tripIds));
    }

    public Map<Long, List<String>> collectFeedLocationImageUrls(
            List<Long> tripIds, Integer startIndex, Integer perTripLimit) {
        if (tripIds == null || tripIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<String>> pathsByTrip = groupPathsByTrip(tripLocationImageRepository.findFeedImagePathsByTripIds(tripIds));

        int from = startIndex != null ? Math.max(0, startIndex) : 0;
        Map<Long, List<String>> slicedPaths = new LinkedHashMap<>();
        List<String> flatToSign = new ArrayList<>();
        List<Long> tripKeyPerPath = new ArrayList<>();

        for (Long tripId : tripIds) {
            List<String> all = pathsByTrip.getOrDefault(tripId, List.of());
            int to = perTripLimit != null ? Math.min(all.size(), from + perTripLimit) : all.size();
            if (from >= all.size()) {
                slicedPaths.put(tripId, List.of());
                continue;
            }
            List<String> slice = all.subList(from, to);
            slicedPaths.put(tripId, new ArrayList<>(slice.size()));
            for (String path : slice) {
                flatToSign.add(path);
                tripKeyPerPath.add(tripId);
            }
        }

        if (flatToSign.isEmpty() || !imageService.isAuthenticatedForSigning()) {
            return slicedPaths;
        }

        List<String> signed = imageService.createSignedReadUrlsIfAuthenticated(flatToSign);
        for (int i = 0; i < signed.size(); i++) {
            String url = signed.get(i);
            if (url != null && !url.isBlank()) {
                slicedPaths.get(tripKeyPerPath.get(i)).add(url);
            }
        }
        return slicedPaths;
    }

    private static Map<Long, List<String>> groupPathsByTrip(List<FeedImagePathRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        return rows.stream()
                .filter(row -> row.imagePath() != null && !row.imagePath().isBlank())
                .collect(
                        Collectors.groupingBy(
                                FeedImagePathRow::tripId,
                                LinkedHashMap::new,
                                Collectors.mapping(FeedImagePathRow::imagePath, Collectors.toList())));
    }

    public List<String> collectLocationImageUrls(TripEntity trip) {
        List<String> urls = new ArrayList<>();
        if (trip.getTripLocations() == null) {
            return urls;
        }
        for (TripLocationEntity tl : trip.getTripLocations()) {
            if (tl.getImages() == null) {
                continue;
            }
            for (TripLocationImageEntity img : tl.getImages()) {
                String url = imageService.createSignedReadUrlIfAuthenticated(img.getImagePath());
                if (url != null && !url.isBlank()) {
                    urls.add(url);
                }
            }
        }
        return urls;
    }

    /** Signed GET URLs per trip-location id (trip detail second stage). */
    public Map<Long, List<TripLocationImageRead>> collectSignedImagesByTripLocationId(
            List<TripLocationEntity> stops) {
        Map<Long, List<TripLocationImageRead>> out = new LinkedHashMap<>();
        if (stops == null) {
            return out;
        }
        stops.stream()
                .sorted(Comparator.comparing(TripLocationEntity::getId))
                .forEach(
                        tl -> {
                            List<TripLocationImageRead> images = new ArrayList<>();
                            if (tl.getImages() != null) {
                                for (TripLocationImageEntity img : tl.getImages()) {
                                    String url =
                                            imageService.createSignedReadUrlIfAuthenticated(
                                                    img.getImagePath());
                                    if (url != null && !url.isBlank()) {
                                        images.add(new TripLocationImageRead(img.getId(), url));
                                    }
                                }
                            }
                            out.put(tl.getId(), images);
                        });
        return out;
    }
}
