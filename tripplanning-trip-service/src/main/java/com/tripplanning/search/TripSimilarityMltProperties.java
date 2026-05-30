package com.tripplanning.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** Tunable More Like This parameters for {@link TripSimilarityServiceEs} and {@link TripSimilarityServiceLucene}. */
@ConfigurationProperties(prefix = "tripplanning.search.mlt")
@Getter
@Setter
public class TripSimilarityMltProperties {

    private int minTermFreq = 1;
    private int minDocFreq = 4;
    private int maxQueryTerms = 15;
    /** Reserved for future request-level filtering; not passed via {@code fromJson} query clauses. */
    private float minScore = 0.01f;
    private float destinationBoost = 3f;
    private float placeNameBoost = 2f;
    private float cityNameBoost = 2f;
    private float titleBoost = 1.5f;
    private float shortDescriptionBoost = 1f;
}
