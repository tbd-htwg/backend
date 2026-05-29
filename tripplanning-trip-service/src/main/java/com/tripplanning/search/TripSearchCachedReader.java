package com.tripplanning.search;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripplanning.config.CacheConfig;
import com.tripplanning.transport.TransportRoutes;
import com.tripplanning.trip.TripEntity;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * Cached Elasticsearch search with fixed-count SQL hydration (no lazy N+1). Lives in its own bean
 * so {@code @Cacheable} fires through the Spring proxy.
 */
@Service
@RequiredArgsConstructor
public class TripSearchCachedReader {

    private final EntityManager entityManager;

    @Cacheable(
            value = CacheConfig.TRIP_SEARCH,
            key = "T(java.util.List).of(#terms.trim().toLowerCase(), #page, #size)")
    @Transactional(readOnly = true)
    public Page<TripSearchDto> searchRaw(String terms, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        if (terms == null || terms.trim().isEmpty()) {
            return Page.empty(pageable);
        }

        String trimmed = terms.trim();
        SearchSession searchSession = Search.session(entityManager);
        int offset = Math.toIntExact(pageable.getOffset());

        SearchResult<Long> result =
                searchSession
                        .search(TripEntity.class)
                        .select(f -> f.field("id", Long.class))
                        .where(
                                f ->
                                        f.bool(
                                                b -> {
                                                    b.should(
                                                            f.match()
                                                                    .field("title")
                                                                    .matching(trimmed)
                                                                    .fuzzy(1));
                                                    b.should(
                                                            f.match()
                                                                    .field("destination")
                                                                    .matching(trimmed)
                                                                    .fuzzy(1));
                                                    b.should(
                                                            f.match()
                                                                    .fields(
                                                                            "shortDescription",
                                                                            "user.name",
                                                                            "tripLocations.placeName",
                                                                            "tripLocations.cityName",
                                                                            "accommodations.name",
                                                                            "transports.startAddress",
                                                                            "transports.endAddress")
                                                                    .matching(trimmed));
                                                    b.minimumShouldMatch(1);
                                                }))
                        .fetch(offset, size);

        List<Long> ids = result.hits();
        long totalHits = result.total().hitCount();
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        List<TripSearchDto> content = hydrateByIds(ids);
        return new PageImpl<>(content, pageable, totalHits);
    }

    private List<TripSearchDto> hydrateByIds(List<Long> ids) {
        Map<Long, SearchHeaderRow> headers = loadHeaders(ids);
        Map<Long, List<String>> locations = batchLocationNamesByTripId(ids);
        Map<Long, List<String>> accomNames = batchAccommodationNamesByTripId(ids);
        Map<Long, List<String>> transportRoutes = batchTransportRoutesByTripId(ids);

        List<TripSearchDto> out = new ArrayList<>(ids.size());
        for (Long id : ids) {
            SearchHeaderRow row = headers.get(id);
            if (row == null) {
                continue;
            }
            out.add(
                    TripSearchDto.builder()
                            .id(row.id())
                            .userId(row.userId())
                            .title(row.title())
                            .author(row.authorName())
                            .shortDescription(row.shortDescription())
                            .destination(row.destination())
                            .startDate(row.startDate())
                            .locations(locations.getOrDefault(id, List.of()))
                            .accommodationNames(accomNames.getOrDefault(id, List.of()))
                            .transportRoutes(transportRoutes.getOrDefault(id, List.of()))
                            .build());
        }
        return out;
    }

    private Map<Long, SearchHeaderRow> loadHeaders(List<Long> ids) {
        List<SearchHeaderRow> rows =
                entityManager
                        .createQuery(
                                "SELECT new com.tripplanning.search.TripSearchCachedReader$SearchHeaderRow("
                                        + "t.id, t.title, t.destination, t.startDate, t.shortDescription,"
                                        + " u.id, u.name)"
                                        + " FROM TripEntity t JOIN t.user u"
                                        + " WHERE t.id IN :ids",
                                SearchHeaderRow.class)
                        .setParameter("ids", ids)
                        .getResultList();
        Map<Long, SearchHeaderRow> out = new LinkedHashMap<>();
        for (SearchHeaderRow row : rows) {
            out.put(row.id(), row);
        }
        return out;
    }

    private Map<Long, List<String>> batchLocationNamesByTripId(List<Long> tripIds) {
        if (tripIds.isEmpty()) {
            return Map.of();
        }
        List<StopNameRow> rows =
                entityManager
                        .createQuery(
                                "SELECT new com.tripplanning.search.TripSearchCachedReader$StopNameRow("
                                        + "tl.trip.id, tl.placeName)"
                                        + " FROM TripLocationEntity tl"
                                        + " WHERE tl.trip.id IN :ids"
                                        + " ORDER BY tl.trip.id, tl.id",
                                StopNameRow.class)
                        .setParameter("ids", tripIds)
                        .getResultList();
        Map<Long, List<String>> out = new LinkedHashMap<>();
        for (StopNameRow row : rows) {
            out.computeIfAbsent(row.tripId(), k -> new ArrayList<>()).add(row.placeName());
        }
        return out;
    }

    private Map<Long, List<String>> batchAccommodationNamesByTripId(List<Long> tripIds) {
        if (tripIds.isEmpty()) {
            return Map.of();
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows =
                entityManager
                        .createQuery(
                                "SELECT t.id, a.name FROM TripEntity t JOIN t.accommodations a"
                                        + " WHERE t.id IN :ids"
                                        + " ORDER BY t.id, a.id")
                        .setParameter("ids", tripIds)
                        .getResultList();
        Map<Long, List<String>> out = new LinkedHashMap<>();
        for (Object[] row : rows) {
            Long tripId = (Long) row[0];
            String name = (String) row[1];
            out.computeIfAbsent(tripId, k -> new ArrayList<>()).add(name);
        }
        return out;
    }

    private Map<Long, List<String>> batchTransportRoutesByTripId(List<Long> tripIds) {
        if (tripIds.isEmpty()) {
            return Map.of();
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows =
                entityManager
                        .createQuery(
                                "SELECT t.id, tr.startAddress, tr.endAddress FROM TripEntity t JOIN t.transports tr"
                                        + " WHERE t.id IN :ids"
                                        + " ORDER BY t.id, tr.id")
                        .setParameter("ids", tripIds)
                        .getResultList();
        Map<Long, List<String>> out = new LinkedHashMap<>();
        for (Object[] row : rows) {
            Long tripId = (Long) row[0];
            String route = TransportRoutes.format((String) row[1], (String) row[2]);
            out.computeIfAbsent(tripId, k -> new ArrayList<>()).add(route);
        }
        return out;
    }

    public record SearchHeaderRow(
            Long id,
            String title,
            String destination,
            LocalDate startDate,
            String shortDescription,
            Long userId,
            String authorName) {}

    private record StopNameRow(Long tripId, String placeName) {}
}
