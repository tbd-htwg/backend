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
        String gcsImagePrefix,
        Integer viralTripInterval,
        Integer viralLikes,
        Integer viralComments) {

    public int viralTripIntervalOrDefault() {
        return viralTripInterval != null && viralTripInterval > 0 ? viralTripInterval : 1000;
    }

    public int viralLikesOrDefault() {
        return viralLikes != null && viralLikes > 0 ? viralLikes : 100;
    }

    public int viralCommentsOrDefault() {
        return viralComments != null && viralComments > 0 ? viralComments : 20;
    }
}
