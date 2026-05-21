package com.tripplanning.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link SearchIndexCoordinationService} index state resolution (via package access). */
class SearchIndexStateTest {

    @Test
    void emptyDatabase_isReady_evenWhenElasticsearchHasOrphanDocs() {
        assertThat(SearchIndexCoordinationService.resolveStateForTest(0, 1, false))
                .isEqualTo(SearchIndexStatus.STATE_READY);
    }

    @Test
    void matchingCounts_isReady() {
        assertThat(SearchIndexCoordinationService.resolveStateForTest(5, 5, false))
                .isEqualTo(SearchIndexStatus.STATE_READY);
    }

    @Test
    void moreIndexedThanDatabase_isReady() {
        assertThat(SearchIndexCoordinationService.resolveStateForTest(3, 5, false))
                .isEqualTo(SearchIndexStatus.STATE_READY);
    }

    @Test
    void databaseHasTripsButIndexEmpty_isEmptyWhenNotIndexing() {
        assertThat(SearchIndexCoordinationService.resolveStateForTest(2, 0, false))
                .isEqualTo(SearchIndexStatus.STATE_EMPTY);
    }
}
