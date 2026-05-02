package com.tripplanning.search;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripplanning.accommodation.AccomEntity;
import com.tripplanning.location.LocationEntity;
import com.tripplanning.transport.TransportEntity;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EntitySearchSuggestService {

    static final int DEFAULT_SUGGEST_LIMIT = 20;
    private static final int BUNDLE_DEFAULT_LIMIT = 8;
    private static final int BUNDLE_MAX_LIMIT = 20;
    /** Minimum prefix length when querying all three indices in one request. */
    static final int MIN_PREFIX_LENGTH_BUNDLE = 2;

    private final EntityManager entityManager;

    /** Escape Lucene/Elasticsearch wildcard special characters in user-supplied prefix. */
    static String escapeWildcard(String prefix) {
        return prefix.replace("\\", "\\\\").replace("*", "\\*").replace("?", "\\?");
    }

    @Transactional(readOnly = true)
    public List<SearchSuggestionDto> suggestTransport(String prefix) {
        return suggestTransportInternal(prefix, DEFAULT_SUGGEST_LIMIT);
    }

    @Transactional(readOnly = true)
    public List<SearchSuggestionDto> suggestLocation(String prefix) {
        return suggestLocationInternal(prefix, DEFAULT_SUGGEST_LIMIT);
    }

    @Transactional(readOnly = true)
    public List<SearchSuggestionDto> suggestAccommodation(String prefix) {
        return suggestAccommodationInternal(prefix, DEFAULT_SUGGEST_LIMIT);
    }

    /**
     * Single round-trip for the UI: up to three sequential Hibernate Search queries (thread-safe).
     * Bundle mode (no scope) skips work unless prefix is long enough. Scoped mode (colon UI) runs
     * one catalog query; empty prefix yields match-all top hits.
     */
    @Transactional(readOnly = true)
    public FacetFilterSuggestResponse suggestFilters(String prefix, Integer limitParam, String scopeRaw) {
        int limit = clampLimit(limitParam);
        Optional<FacetSuggestScope> scope = FacetSuggestScope.parse(scopeRaw);
        String p = normalizePrefix(prefix);

        if (scope.isEmpty()) {
            if (p.length() < MIN_PREFIX_LENGTH_BUNDLE) {
                return FacetFilterSuggestResponse.empty();
            }
            return FacetFilterSuggestResponse.builder()
                    .transports(suggestTransportInternal(p, limit))
                    .locations(suggestLocationInternal(p, limit))
                    .accommodations(suggestAccommodationInternal(p, limit))
                    .build();
        }

        return switch (scope.get()) {
            case TRANSPORT -> FacetFilterSuggestResponse.builder()
                    .transports(suggestTransportInternal(p, limit))
                    .locations(List.of())
                    .accommodations(List.of())
                    .build();
            case LOCATION -> FacetFilterSuggestResponse.builder()
                    .transports(List.of())
                    .locations(suggestLocationInternal(p, limit))
                    .accommodations(List.of())
                    .build();
            case ACCOMMODATION -> FacetFilterSuggestResponse.builder()
                    .transports(List.of())
                    .locations(List.of())
                    .accommodations(suggestAccommodationInternal(p, limit))
                    .build();
        };
    }

    private static int clampLimit(Integer limitParam) {
        if (limitParam == null || limitParam < 1) {
            return BUNDLE_DEFAULT_LIMIT;
        }
        return Math.min(limitParam, BUNDLE_MAX_LIMIT);
    }

    private static String normalizePrefix(String prefix) {
        return prefix == null ? "" : prefix.trim().toLowerCase(Locale.ROOT);
    }

    private List<SearchSuggestionDto> suggestTransportInternal(String normalizedPrefix, int limit) {
        SearchSession session = Search.session(entityManager);
        SearchResult<TransportEntity> result =
                session.search(TransportEntity.class)
                        .where(f -> {
                            if (normalizedPrefix.isEmpty()) {
                                return f.matchAll();
                            }
                            return f.wildcard().field("type_keyword").matching(escapeWildcard(normalizedPrefix) + "*");
                        })
                        .fetch(0, limit);
        return result.hits().stream()
                .map(t -> SearchSuggestionDto.builder()
                        .id(t.getId())
                        .label(t.getType())
                        .secondary(null)
                        .build())
                .collect(Collectors.toList());
    }

    private List<SearchSuggestionDto> suggestLocationInternal(String normalizedPrefix, int limit) {
        SearchSession session = Search.session(entityManager);
        SearchResult<LocationEntity> result =
                session.search(LocationEntity.class)
                        .where(f -> {
                            if (normalizedPrefix.isEmpty()) {
                                return f.matchAll();
                            }
                            return f.wildcard()
                                    .field("destination_keyword")
                                    .matching(escapeWildcard(normalizedPrefix) + "*");
                        })
                        .fetch(0, limit);
        return result.hits().stream()
                .map(loc -> SearchSuggestionDto.builder()
                        .id(loc.getId())
                        .label(loc.getName())
                        .secondary(null)
                        .build())
                .collect(Collectors.toList());
    }

    private List<SearchSuggestionDto> suggestAccommodationInternal(String normalizedPrefix, int limit) {
        SearchSession session = Search.session(entityManager);
        SearchResult<AccomEntity> result =
                session.search(AccomEntity.class)
                        .where(f -> {
                            if (normalizedPrefix.isEmpty()) {
                                return f.matchAll();
                            }
                            return f.wildcard().field("type_keyword").matching(escapeWildcard(normalizedPrefix) + "*");
                        })
                        .fetch(0, limit);
        return result.hits().stream()
                .map(a -> SearchSuggestionDto.builder()
                        .id(a.getId())
                        .label(a.getType())
                        .secondary(a.getName())
                        .build())
                .collect(Collectors.toList());
    }
}
