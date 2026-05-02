package com.tripplanning.images;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.tripplanning.trip.TripEntity;
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
}
