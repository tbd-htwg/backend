package com.tripplanning.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ViralTripSupportTest {

    @Test
    void selectsEveryNthTripAsViral() {
        List<Long> all = List.of(1L, 999L, 1000L, 1001L, 2000L, 3000L);
        assertThat(ViralTripSupport.viralTripIds(all, 1000)).containsExactly(1000L, 2000L, 3000L);
    }

    @Test
    void picksCanonicalViralTripNearMiddle() {
        List<Long> viral = ViralTripSupport.viralTripIds(List.of(1000L, 2000L, 3000L, 4000L, 5000L), 1000);
        assertThat(ViralTripSupport.canonicalViralTripId(viral)).isEqualTo(3000L);
    }

    @Test
    void samplesDistinctUsers() {
        List<Long> sample = ViralTripSupport.sampleDistinctUserIds(50, 20, new java.util.Random(42));
        assertThat(sample).hasSize(20);
        assertThat(sample.stream().distinct().count()).isEqualTo(20);
    }
}
