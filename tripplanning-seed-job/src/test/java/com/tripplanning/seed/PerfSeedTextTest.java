package com.tripplanning.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.tripplanning.seed.assets.PlaceSeedCategory;
import com.tripplanning.seed.assets.PrefetchedPlace;

class PerfSeedTextTest {

    @Test
    void generatesPerformanceStyleTripAndStopCopy() {
        Random rng = new Random(42);
        PrefetchedPlace dest =
                new PrefetchedPlace("id", "Paris", "Paris", "Paris, France", 48.8, 2.3, "FR", "CITY");
        PerfSeedText.TripTopic topic = PerfSeedText.pickTripTopic(rng);
        String title = PerfSeedText.tripTitle(topic);

        assertThat(title).isEqualTo(topic.topic());
        assertThat(title).doesNotContain("#");
        assertThat(title).doesNotContain("Perf trip");
        assertThat(title).doesNotContain("Paris");
        assertThat(title).doesNotContain("—");
        assertThat(topic.shortDescription()).isNotBlank();

        String longDesc = PerfSeedText.tripLongDescription(rng, topic, dest, 7);
        assertThat(longDesc).isNotBlank();
        assertThat(longDesc).contains("Seed dataset trip #7");
        assertThat(longDesc).contains("Paris");
        assertThat(longDesc.toLowerCase()).contains(topic.topic().toLowerCase());

        String cafeStop = PerfSeedText.stopDescription(rng, PlaceSeedCategory.CAFE, "Corner Cafe");
        assertThat(cafeStop).doesNotContain("Stop 1 for trip");
        assertThat(cafeStop.toLowerCase()).containsAnyOf("coffee", "espresso", "cafe", "bakery", "pastry");

        String museumStop = PerfSeedText.stopDescription(rng, PlaceSeedCategory.MUSEUM, "City Museum");
        assertThat(museumStop.toLowerCase()).containsAnyOf("museum", "collection", "gallery", "audio");

        assertThat(PerfSeedText.comment(rng, 99, 12)).doesNotContain("Social graph");
    }

    @Test
    void preferredStopCategoriesAlignWithFoodTopics() {
        PerfSeedText.TripTopic food = findTopic("Food Tour");
        assertThat(PerfSeedText.preferredStopCategories(food))
                .contains(PlaceSeedCategory.RESTAURANT, PlaceSeedCategory.CAFE);
    }

    @Test
    void alpineTopicsPreferAlpineCountries() {
        PerfSeedText.TripTopic alpine = findTopic("Alpine Crossing");
        Set<String> countries = PerfSeedText.preferredCountryCodes(alpine);
        assertThat(countries).contains("CH", "AT", "FR");
    }

    private static PerfSeedText.TripTopic findTopic(String name) {
        Random rng = new Random(1);
        for (int i = 0; i < 500; i++) {
            PerfSeedText.TripTopic topic = PerfSeedText.pickTripTopic(rng);
            if (topic.topic().equals(name)) {
                return topic;
            }
        }
        throw new AssertionError("topic not found: " + name);
    }
}
