package com.tripplanning.search;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queries.mlt.MoreLikeThis;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.hibernate.search.backend.lucene.LuceneExtension;
import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripplanning.trip.TripEntity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Lucene-backed "More Like This" for JVM-only local ({@code local & !k8s}) and {@code test}.
 * Minikube/GKE use OpenSearch via {@link TripSimilarityServiceEs} instead.
 */
@Service
@Profile({"test", "local & !k8s"})
@RequiredArgsConstructor
@Slf4j
public class TripSimilarityServiceLucene implements TripSimilarityPort {

    private final EntityManager entityManager;
    private final EntityManagerFactory entityManagerFactory;
    private final TripSimilarityMltProperties mltProperties;

    @Override
    @Transactional(readOnly = true)
    public Page<TripSearchDto> findSimilar(List<Long> referenceIds, List<Long> excludeIds, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        if (referenceIds == null || referenceIds.isEmpty()) {
            return Page.empty(pageable);
        }

        List<TripEntity> refs = referenceIds.stream()
                .map(id -> entityManager.find(TripEntity.class, id))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (refs.isEmpty()) {
            return Page.empty(pageable);
        }

        SearchSession searchSession = Search.session(entityManager);

        // HS7: IndexReader must be obtained via SearchMapping, not via SearchSession.extension()
        try (IndexReader indexReader = Search.mapping(entityManagerFactory)
                .scope(TripEntity.class)
                .extension(LuceneExtension.get())
                .openIndexReader()) {

            Query mltQuery = buildMltQuery(indexReader, refs);

            int offset = Math.toIntExact(pageable.getOffset());
            SearchResult<TripEntity> result = searchSession.search(TripEntity.class)
                    .where(f -> {
                        var mlt = f.extension(LuceneExtension.get()).fromLuceneQuery(mltQuery);
                        if (excludeIds == null || excludeIds.isEmpty()) {
                            return mlt;
                        }
                        var bool = f.bool();
                        bool.must(mlt);
                        for (Long excludeId : excludeIds) {
                            bool.mustNot(f.id().matching(excludeId));
                        }
                        return bool;
                    })
                    .fetch(offset, size);

            List<TripSearchDto> content = result.hits().stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());

            return new PageImpl<>(content, pageable, result.total().hitCount());

        } catch (IOException e) {
            log.warn("Lucene MLT query failed, returning empty page: {}", e.getMessage());
            return Page.empty(pageable);
        }
    }

    /**
     * Builds a boolean OR of MLT sub-queries, one per reference entity.
     * Uses {@link MoreLikeThis#like(Map)} with extracted text content instead of
     * Lucene doc IDs – avoids the need to resolve internal document numbers.
     */
    private Query buildMltQuery(IndexReader indexReader, List<TripEntity> refs) throws IOException {
        MoreLikeThis mlt = new MoreLikeThis(indexReader);
        mlt.setAnalyzer(new EnglishAnalyzer());
        mlt.setFieldNames(new String[]{"title", "shortDescription", "destination"});
        mlt.setMinTermFreq(mltProperties.getMinTermFreq());
        mlt.setMinDocFreq(mltProperties.getMinDocFreq());
        mlt.setMaxQueryTerms(mltProperties.getMaxQueryTerms());

        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (TripEntity ref : refs) {
            builder.add(mlt.like(toDocumentMap(ref)), BooleanClause.Occur.SHOULD);
        }
        return builder.build();
    }

    /**
     * Converts the text fields of a {@link TripEntity} into the map format
     * expected by {@link MoreLikeThis#like(Map)}.
     */
    private static Map<String, Collection<Object>> toDocumentMap(TripEntity ref) {
        Map<String, Collection<Object>> doc = new HashMap<>();
        if (ref.getTitle() != null)            doc.put("title",            List.of(ref.getTitle()));
        if (ref.getShortDescription() != null) doc.put("shortDescription", List.of(ref.getShortDescription()));
        if (ref.getDestination() != null)      doc.put("destination",      List.of(ref.getDestination()));
        ref.getTripLocations().stream()
                .map(tl -> tl.getPlaceName())
                .filter(name -> name != null && !name.isBlank())
                .forEach(name -> doc.merge("tripLocations.placeName", List.of(name), (a, b) -> {
                    List<Object> merged = new java.util.ArrayList<>(a);
                    merged.addAll(b);
                    return merged;
                }));
        return doc;
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
