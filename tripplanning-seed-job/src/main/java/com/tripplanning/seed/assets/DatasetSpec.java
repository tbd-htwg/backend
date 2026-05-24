package com.tripplanning.seed.assets;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DatasetSpec(
        int totalUsers,
        int totalTrips,
        int tripsPerUserMin,
        int tripsPerUserMax,
        double socialTripFraction,
        int minCommentsPerUser,
        int minLikesPerUser,
        int tripLocationsPerTrip,
        int accommodationsPerTrip,
        int transportsPerTrip,
        int imagePathsPerStopMin,
        int imagePathsPerStopMax,
        String gcsImagePrefix) {}
