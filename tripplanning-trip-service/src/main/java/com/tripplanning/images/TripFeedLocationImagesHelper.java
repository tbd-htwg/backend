package com.tripplanning.images;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tripplanning.trip.TripEntity;
import com.tripplanning.trip.read.TripFeedDtos.TripLocationImageRead;
import com.tripplanning.tripLocation.TripLocationEntity;
import com.tripplanning.tripLocation.TripLocationImageEntity;

import lombok.RequiredArgsConstructor;

/**
 * Flattens trip-location images into signed GET URLs for feed cards (authenticated requests only).
 */
@Component
@RequiredArgsConstructor
public class TripFeedLocationImagesHelper {

    private final ImageService imageService;

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
