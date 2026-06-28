package com.tripplanning.search;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.hibernate.search.backend.elasticsearch.ElasticsearchExtension;
import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.hibernate.search.util.common.SearchException;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripplanning.tenant.TenantSearchIndexResolver;
import com.tripplanning.trip.TripEntity;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Elasticsearch/OpenSearch-backed "More Like This" for {@code k8s} (minikube, GKE).
 * Sends a native MLT JSON query via {@link ElasticsearchExtension}.
 */
@Service
@Profile("k8s")
@RequiredArgsConstructor
@Slf4j
public class TripSimilarityServiceEs implements TripSimilarityPort {

    /** Plain fields only — nested {@code tripLocations.*} collapse multi-anchor MLT to zero hits. */
    private static final String[] MLT_FIELDS = {
        "destination", "title", "shortDescription"
    };

    private final EntityManager entityManager;
    private final TripSimilarityMltProperties mltProperties;
    private final TenantSearchIndexResolver tenantSearchIndexResolver;

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

        try {
            int offset = Math.toIntExact(pageable.getOffset());
            SearchResult<TripEntity> result = searchSession.search(TripEntity.class)
                    .extension(ElasticsearchExtension.get())
                    .where(f -> f.fromJson(queryJson))
                    .fetch(offset, size);

            long totalHits = result.total().hitCount();
            if (totalHits == 0) {
                log.info(
                        "MLT returned no hits for referenceIds={} (readIndex={})",
                        referenceIds,
                        readIndexName());
            }

            List<TripSearchDto> content = result.hits().stream()
                    .filter(TripEntity::isVisible)
                    .map(this::toDto)
                    .collect(Collectors.toList());

            return new PageImpl<>(content, pageable, totalHits);
        } catch (SearchException e) {
            log.warn("MLT query failed for referenceIds={}: {}", referenceIds, e.getMessage());
            return Page.empty(pageable);
        }
    }

    /**
     * Hibernate Search exposes {@code {indexName}-read} / {@code {indexName}-write} aliases; MLT
     * {@code like} document references must use the read alias, not the logical index name alone.
     */
    private String readIndexName() {
        return tenantSearchIndexResolver.currentIndex() + "-read";
    }

    /**
     * Builds the native Elasticsearch "more_like_this" JSON query. Field boosts are applied via
     * optional {@code should} destination matches — caret boosts in MLT {@code fields} break
     * OpenSearch MLT on our cluster (zero hits).
     */
    private String buildMltQueryJson(List<Long> referenceIds, List<Long> excludeIds) {
        String fieldsJson = buildFieldsJson();
        String likeJson = buildLikeJson(referenceIds);
        String mltClause =
                """
                {
                  "more_like_this": {
                    "fields": %s,
                    "like": %s,
                    "min_term_freq": %d,
                    "min_doc_freq": %d,
                    "max_query_terms": %d
                  }
                }
                """
                        .formatted(
                                fieldsJson,
                                likeJson,
                                mltProperties.getMinTermFreq(),
                                mltProperties.getMinDocFreq(),
                                mltProperties.getMaxQueryTerms());

        String destinationShould = buildDestinationShouldJson(referenceIds);
        boolean hasExclude = excludeIds != null && !excludeIds.isEmpty();
        boolean hasDestinationBoost = !destinationShould.isEmpty();

        if (!hasExclude && !hasDestinationBoost) {
            return mltClause;
        }

        StringBuilder boolInner = new StringBuilder();
        boolInner.append("\"must\": ").append(mltClause);
        if (hasExclude) {
            boolInner.append(", \"must_not\": ").append(buildMustNotJson(excludeIds));
        }
        if (hasDestinationBoost) {
            boolInner.append(", \"should\": ").append(destinationShould);
            boolInner.append(", \"minimum_should_match\": 0");
        }

        return """
                {
                  "bool": {
                    %s
                  }
                }
                """
                .formatted(boolInner);
    }

    private static String buildFieldsJson() {
        return "[" + java.util.Arrays.stream(MLT_FIELDS)
                .map(f -> "\"" + f + "\"")
                .collect(Collectors.joining(", ")) + "]";
    }

    private String buildLikeJson(List<Long> ids) {
        String readIndex = readIndexName();
        String docs = ids.stream()
                .map(id -> "{ \"_index\": \"%s\", \"_id\": \"%d\" }".formatted(readIndex, id))
                .collect(Collectors.joining(", "));
        return "[" + docs + "]";
    }

    /** Optional destination match boost derived from anchor trips. */
    private String buildDestinationShouldJson(List<Long> referenceIds) {
        List<String> destinations =
                referenceIds.stream()
                        .map(id -> entityManager.find(TripEntity.class, id))
                        .filter(Objects::nonNull)
                        .map(TripEntity::getDestination)
                        .filter(d -> d != null && !d.isBlank())
                        .distinct()
                        .toList();

        if (destinations.isEmpty()) {
            return "";
        }

        String matches =
                destinations.stream()
                        .map(
                                d ->
                                        "{ \"match\": { \"destination\": { \"query\": \""
                                                + escapeJson(d)
                                                + "\", \"boost\": "
                                                + mltProperties.getDestinationBoost()
                                                + " } } }")
                        .collect(Collectors.joining(", "));
        return "[" + matches + "]";
    }

    private static String buildMustNotJson(List<Long> excludeIds) {
        String values =
                excludeIds.stream()
                        .map(id -> "\"" + id + "\"")
                        .collect(Collectors.joining(", "));
        return "[{ \"ids\": { \"values\": [" + values + "] } }]";
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
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
