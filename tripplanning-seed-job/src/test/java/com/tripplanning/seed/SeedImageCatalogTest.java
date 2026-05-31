package com.tripplanning.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.tripplanning.seed.assets.PlaceSeedCategory;
import com.tripplanning.seed.assets.PlaceSeedSupport;
import com.tripplanning.seed.assets.PrefetchedPlace;
import com.tripplanning.seed.assets.SampleImageRow;
import com.tripplanning.seed.assets.SeedImageCatalog;

class SeedImageCatalogTest {

    @Test
    void picksCategoryAndRegionMatchedImages() {
        List<SampleImageRow> rows =
                List.of(
                        new SampleImageRow(
                                "cafe",
                                "1",
                                "a",
                                "cafe/a.jpg",
                                "sample/cafe/a.jpg",
                                "image",
                                "europe"),
                        new SampleImageRow(
                                "cafe",
                                "2",
                                "b",
                                "cafe/b.jpg",
                                "sample/cafe/b.jpg",
                                "image",
                                "generic"),
                        new SampleImageRow(
                                "tourism",
                                "3",
                                "c",
                                "tourism/c.jpg",
                                "sample/tourism/c.jpg",
                                "image",
                                "europe"));
        SeedImageCatalog catalog = new SeedImageCatalog(rows);
        List<String> paths =
                catalog.pickPaths(PlaceSeedCategory.MUSEUM, "FR", 2, new Random(42));
        assertThat(paths).isNotEmpty();
        assertThat(paths).allMatch(path -> path.contains("tourism") || path.contains("castle"));
    }

    @Test
    void resolvesRegionBuckets() {
        assertThat(PlaceSeedSupport.regionBucket("FR")).isEqualTo("europe");
        assertThat(PlaceSeedSupport.regionBucket("JP")).isEqualTo("asia");
        assertThat(PlaceSeedSupport.regionBucket("US")).isEqualTo("americas");
    }

    @Test
    void infersLodgingFromHotelQuery() {
        PrefetchedPlace hotel =
                new PrefetchedPlace(
                        "h1",
                        "Paris Hotel Marais",
                        "Paris",
                        "Paris, France",
                        48.8,
                        2.3,
                        "FR",
                        null);
        assertThat(PlaceSeedSupport.resolveCategory(hotel)).isEqualTo(PlaceSeedCategory.LODGING);
    }
}
