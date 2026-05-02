package com.tripplanning.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SearchQueryParserTest {

    private final SearchQueryParser parser = new SearchQueryParser();

    @Test
    void emptyAndBlank() {
        assertThat(parser.parse(null).facets()).isEmpty();
        assertThat(parser.parse("  ").freeText()).isEmpty();
    }

    @Test
    void freeTextOnly() {
        ParsedSearchQuery p = parser.parse("weekend in paris");
        assertThat(p.facets()).isEmpty();
        assertThat(p.freeText()).isEqualTo("weekend in paris");
    }

    @Test
    void singleFacet() {
        ParsedSearchQuery p = parser.parse("transport:train");
        assertThat(p.facets()).hasSize(1);
        assertThat(p.facets().get(0).key()).isEqualTo(SearchFacetKey.TRANSPORT);
        assertThat(p.facets().get(0).normalizedValue()).isEqualTo("train");
        assertThat(p.freeText()).isEmpty();
    }

    @Test
    void facetAndFreeText() {
        ParsedSearchQuery p = parser.parse("paris trip transport:train");
        assertThat(p.facets()).hasSize(1);
        assertThat(p.freeText()).isEqualTo("paris trip");
    }

    @Test
    void typoAccomodationAndAliases() {
        ParsedSearchQuery p = parser.parse("accomodation:hotel author:Alice");
        assertThat(p.facets()).hasSize(2);
        assertThat(p.facets().get(0).key()).isEqualTo(SearchFacetKey.ACCOMMODATION);
        assertThat(p.facets().get(0).normalizedValue()).isEqualTo("hotel");
        assertThat(p.facets().get(1).key()).isEqualTo(SearchFacetKey.AUTHOR);
        assertThat(p.facets().get(1).normalizedValue()).isEqualTo("alice");
    }

    @Test
    void userAliasMatchesAuthor() {
        ParsedSearchQuery p = parser.parse("user:Bob");
        assertThat(p.facets().get(0).key()).isEqualTo(SearchFacetKey.AUTHOR);
    }

    @Test
    void preservesTrailingSpaceInFreeText() {
        ParsedSearchQuery p = parser.parse("paris ");
        assertThat(p.facets()).isEmpty();
        assertThat(p.freeText()).isEqualTo("paris ");
    }
}
