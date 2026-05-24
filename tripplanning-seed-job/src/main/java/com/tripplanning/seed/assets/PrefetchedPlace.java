package com.tripplanning.seed.assets;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PrefetchedPlace(
        String googlePlaceId,
        String placeName,
        String cityName,
        String formattedAddress,
        double latitude,
        double longitude,
        String countryCode) {}
