package com.tripplanning.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Hibernate Search + bundled facet suggests (Lucene in-memory for {@code test} profile).
 */
@SpringBootTest
@ActiveProfiles("test")
class EntitySearchSuggestServiceFilterIntegrationTest {

    @Autowired
    private EntitySearchSuggestService entitySearchSuggestService;

    @Test
    void bundleModeSkipsTriplesWhenPrefixTooShort() {
        FacetFilterSuggestResponse r = entitySearchSuggestService.suggestFilters("p", 8, null);
        assertThat(r.getTransports()).isEmpty();
        assertThat(r.getLocations()).isEmpty();
        assertThat(r.getAccommodations()).isEmpty();
    }

    @Test
    void bundleModeSkipsWhenPrefixBlank() {
        FacetFilterSuggestResponse r = entitySearchSuggestService.suggestFilters("   ", 8, null);
        assertThat(r.getTransports()).isEmpty();
        assertThat(r.getLocations()).isEmpty();
        assertThat(r.getAccommodations()).isEmpty();
    }

    @Test
    void scopedModeAllowsShortPrefix() {
        FacetFilterSuggestResponse r =
                entitySearchSuggestService.suggestFilters("t", 8, "transport");
        assertThat(r.getLocations()).isEmpty();
        assertThat(r.getAccommodations()).isEmpty();
    }
}
