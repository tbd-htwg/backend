package com.tripplanning.search;

import java.util.List;
import java.util.stream.Collectors;

import org.hibernate.search.backend.elasticsearch.ElasticsearchExtension;
import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripplanning.trip.TripEntity;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Elasticsearch-backed "More Like This" implementation for all non-local profiles.
 * Sends a native MLT JSON query via {@link ElasticsearchExtension}.
 */
@Service
@Profile("!local")
@RequiredArgsConstructor
@Slf4j
public class TripSimilarityServiceEs implements TripSimilarityPort {

    private static final String[] MLT_FIELDS = {
            "title", "shortDescription", "destination",
            "tripLocations.placeName", "tripLocations.cityName"
    };

    private final EntityManager entityManager;

    @Value("${tripplanning.search.elasticsearch-index-name:tripentity}")
    private String indexName;

    @Override
    @Transactional(readOnly = true)
    public Page<TripSearchDto> findSimilar(List<Long> referenceIds, List<Long> excludeIds, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        if (referenceIds == null || referenceIds.isEmpty()) {
            return Page.empty(pageable);
        }

        SearchSession searchSession = Search.session(entityManager);
        String queryJson = buildMltQueryJson(referenceIds, excludeIds);

        log.debug("MLT query for referenceIds={}: {}", referenceIds, queryJson);

        int offset = Math.toIntExact(pageable.getOffset());
        SearchResult<TripEntity> result = searchSession.search(TripEntity.class)
                .extension(ElasticsearchExtension.get())
                .where(f -> f.fromJson(queryJson))
                .fetch(offset, size);

        List<TripSearchDto> content = result.hits().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return new PageImpl<>(content, pageable, result.total().hitCount());
    }

    /**
     * Builds the native Elasticsearch "more_like_this" JSON query.
     * Reference documents are identified by index name + string ID (Hibernate Search default).
     * Reference IDs are also excluded via a must_not ids filter.
     */
    private String buildMltQueryJson(List<Long> referenceIds, List<Long> excludeIds) {
        String fieldsJson = buildFieldsJson();
        String likeJson = buildLikeJson(referenceIds);
        String mustNotJson = (excludeIds != null && !excludeIds.isEmpty())
                ? buildMustNotJson(excludeIds)
                : "";

        if (mustNotJson.isEmpty()) {
            return """
                    {
                      "more_like_this": {
                        "fields": %s,
                        "like": %s,
                        "min_term_freq": 1,
                        "min_doc_freq": 1,
                        "max_query_terms": 25
                      }
                    }
                    """.formatted(fieldsJson, likeJson);
        }

        return """
                {
                  "bool": {
                    "must": {
                      "more_like_this": {
                        "fields": %s,
                        "like": %s,
                        "min_term_freq": 1,
                        "min_doc_freq": 1,
                        "max_query_terms": 25
                      }
                    },
                    "must_not": %s
                  }
                }
                """.formatted(fieldsJson, likeJson, mustNotJson);
    }

    private String buildFieldsJson() {
        return "[" + java.util.Arrays.stream(MLT_FIELDS)
                .map(f -> "\"" + f + "\"")
                .collect(Collectors.joining(", ")) + "]";
    }

    private String buildLikeJson(List<Long> ids) {
        String docs = ids.stream()
                .map(id -> "{ \"_index\": \"%s\", \"_id\": \"%d\" }".formatted(indexName, id))
                .collect(Collectors.joining(", "));
        return "[" + docs + "]";
    }

    private static String buildMustNotJson(List<Long> excludeIds) {
        String values = excludeIds.stream()
                .map(id -> "\"" + id + "\"")
                .collect(Collectors.joining(", "));
        return "{ \"ids\": { \"values\": [" + values + "] } }";
    }

    private TripSearchDto toDto(TripEntity trip) {
        return TripSearchDto.builder()
                .id(trip.getId())
                .userId(trip.getUser() != null ? trip.getUser().getId() : null)
                .title(trip.getTitle())
                .author(trip.getUser() != null ? trip.getUser().getName() : "Unknown")
                .shortDescription(trip.getShortDescription())
                .destination(trip.getDestination())
                .startDate(trip.getStartDate())
                .locations(trip.getTripLocations().stream()
                        .map(tl -> tl.getPlaceName())
                        .collect(Collectors.toList()))
                .accommodationNames(trip.getAccommodationNames())
                .transportRoutes(trip.getTransportRoutes())
                .build();
    }
}
