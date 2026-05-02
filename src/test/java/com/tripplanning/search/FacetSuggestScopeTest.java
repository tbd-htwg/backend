package com.tripplanning.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FacetSuggestScopeTest {

    @Test
    void parsesTransportLocationAccommodation() {
        assertThat(FacetSuggestScope.parse("transport")).contains(FacetSuggestScope.TRANSPORT);
        assertThat(FacetSuggestScope.parse("LOCATION")).contains(FacetSuggestScope.LOCATION);
        assertThat(FacetSuggestScope.parse("accommodation")).contains(FacetSuggestScope.ACCOMMODATION);
        assertThat(FacetSuggestScope.parse("accomodation")).contains(FacetSuggestScope.ACCOMMODATION);
    }

    @Test
    void emptyMeansAllCatalogs() {
        assertThat(FacetSuggestScope.parse(null)).isEmpty();
        assertThat(FacetSuggestScope.parse("")).isEmpty();
        assertThat(FacetSuggestScope.parse("  ")).isEmpty();
    }
}
