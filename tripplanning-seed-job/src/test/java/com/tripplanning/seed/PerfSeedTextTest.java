package com.tripplanning.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class PerfSeedTextTest {

    @Test
    void generatesPerformanceStyleTripAndStopCopy() {
        Random rng = new Random(42);
        PerfSeedText.TripTopic topic = PerfSeedText.pickTripTopic(rng);
        String title = PerfSeedText.tripTitle(topic, 7);

        assertThat(title).matches("^.+ #7$");
        assertThat(title).doesNotContain("Perf trip");
        assertThat(topic.shortDescription()).isNotBlank();
        assertThat(PerfSeedText.tripLongDescription(rng)).isNotBlank();

        List<String> stops = PerfSeedText.stopDescriptions(rng, 2);
        assertThat(stops).hasSize(2);
        assertThat(stops.get(0)).doesNotContain("Stop 1 for trip");
        assertThat(PerfSeedText.comment(rng, 99, 12)).doesNotContain("Social graph");
    }
}
