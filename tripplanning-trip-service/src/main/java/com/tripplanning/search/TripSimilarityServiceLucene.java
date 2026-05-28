package com.tripplanning.search;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queries.mlt.MoreLikeThis;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.util.BytesRef;
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
 * Lucene-backed "More Like This" implementation for the {@code local} profile.
 * Uses Apache Lucene's {@link MoreLikeThis} with text content extracted from the
 * reference entities. The IndexReader is obtained via {@link EntityManagerFactory}
 * (HS7-compatible; SearchSession.extension() was removed in HS7).
 */
@Service
@Profile("local")
@RequiredArgsConstructor
@Slf4j
public class TripSimilarityServiceLucene implements TripSimilarityPort {

    private final EntityManager entityManager;
    private final EntityManagerFactory entityManagerFactory;

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
            Query finalQuery = excludeIds == null || excludeIds.isEmpty()
                    ? mltQuery
                    : wrapWithExclusion(mltQuery, excludeIds);

            int offset = Math.toIntExact(pageable.getOffset());
            SearchResult<TripEntity> result = searchSession.search(TripEntity.class)
                    .extension(LuceneExtension.get())
                    .where(f -> f.fromLuceneQuery(finalQuery))
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
    private static Query buildMltQuery(IndexReader indexReader, List<TripEntity> refs) throws IOException {
        MoreLikeThis mlt = new MoreLikeThis(indexReader);
        mlt.setFieldNames(new String[]{"title", "shortDescription", "destination"});
        mlt.setMinTermFreq(1);
        mlt.setMinDocFreq(1);
        mlt.setMaxQueryTerms(25);

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
        return doc;
    }

    /**
     * Wraps the MLT query in a boolean MUST + MUST_NOT to exclude the reference trips themselves.
     */
    private static Query wrapWithExclusion(Query mltQuery, List<Long> excludeIds) {
        List<BytesRef> excludeTerms = excludeIds.stream()
                .map(id -> new BytesRef(id.toString()))
                .collect(Collectors.toList());

        return new BooleanQuery.Builder()
                .add(mltQuery, BooleanClause.Occur.MUST)
                .add(new TermInSetQuery("id", excludeTerms), BooleanClause.Occur.MUST_NOT)
                .build();
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

