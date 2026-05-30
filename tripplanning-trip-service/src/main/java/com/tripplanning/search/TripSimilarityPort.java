package com.tripplanning.search;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Abstraction over the "More Like This" query.
 * Spring injects the Lucene impl on profile {@code local},
 * and the Elasticsearch impl on all other profiles.
 */
public interface TripSimilarityPort {

    /**
     * Returns trips that are similar to the given reference trips.
     *
     * @param referenceIds trip IDs to use as similarity anchors (own trips + liked trips)
     * @param excludeIds   trip IDs to exclude from results (typically same as referenceIds)
     * @param page         zero-based page index
     * @param size         page size (max 50)
     * @return paginated list of similar trips as {@link TripSearchDto}
     */
    Page<TripSearchDto> findSimilar(List<Long> referenceIds, List<Long> excludeIds, int page, int size);
}
