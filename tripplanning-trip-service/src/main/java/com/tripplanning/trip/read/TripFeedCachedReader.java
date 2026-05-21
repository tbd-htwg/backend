package com.tripplanning.trip.read;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.tripplanning.config.CacheConfig;
import com.tripplanning.transport.TransportRoutes;
import com.tripplanning.common.client.SocialServiceClient;
import com.tripplanning.trip.TripRepository;
import com.tripplanning.trip.read.TripFeedDtos.TripFeedAccommodation;
import com.tripplanning.trip.read.TripFeedDtos.TripFeedTransport;
import com.tripplanning.tripLocation.TripLocationEntity;
import com.tripplanning.tripLocation.TripLocationImageEntity;
import com.tripplanning.tripLocation.TripLocationRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;

/**
 * SQL-only read layer for the trip feed and trip detail. Returns "raw" DTOs that carry GCS object
 * paths instead of signed URLs, so the cached representation is independent of per-request
 * authentication state.
 *
 * <p>Lives in its own bean so that {@link TripFeedService} can call into it through the Spring
 * proxy and the {@code @Cacheable} aspect actually fires (self-invocation inside one bean would
 * bypass the proxy and silently disable caching).
 */
@Service
@RequiredArgsConstructor
public class TripFeedCachedReader {

    private final EntityManager entityManager;
    private final TripRepository tripRepository;
    private final TripLocationRepository tripLocationRepository;
    private final SocialServiceClient socialServiceClient;

    @Cacheable(value = CacheConfig.TRIP_EXISTS, key = "#tripId")
    @Transactional(readOnly = true)
    public boolean tripExists(long tripId) {
        return tripRepository.existsById(tripId);
    }

    @Cacheable(value = CacheConfig.TRIP_FEED_PAGE, key = "T(java.util.List).of(#page, #size)")
    @Transactional(readOnly = true)
    public TripFeedPageRaw feedRaw(int page, int size) {
        long totalItems = countAll();
        if (totalItems == 0) {
            return emptyRawPage(page, size);
        }
        List<TripHeaderRow> headers =
                queryHeaders(
                        "SELECT new com.tripplanning.trip.read.TripFeedCachedReader$TripHeaderRow("
                                + "t.id, t.title, t.destination, t.startDate, t.shortDescription,"
                                + " u.id, u.name, u.imagePath)"
                                + " FROM TripEntity t JOIN t.user u"
                                + " ORDER BY t.id DESC",
                        Map.of(),
                        page,
                        size);
        return assembleRawPage(headers, page, size, totalItems);
    }

    @Cacheable(value = CacheConfig.TRIP_FEED_BY_USER, key = "T(java.util.List).of(#userId, #page, #size)")
    @Transactional(readOnly = true)
    public TripFeedPageRaw feedByUserRaw(long userId, int page, int size) {
        long totalItems = countByUser(userId);
        if (totalItems == 0) {
            return emptyRawPage(page, size);
        }
        List<TripHeaderRow> headers =
                queryHeaders(
                        "SELECT new com.tripplanning.trip.read.TripFeedCachedReader$TripHeaderRow("
                                + "t.id, t.title, t.destination, t.startDate, t.shortDescription,"
                                + " u.id, u.name, u.imagePath)"
                                + " FROM TripEntity t JOIN t.user u"
                                + " WHERE u.id = :userId"
                                + " ORDER BY t.id DESC",
                        Map.of("userId", userId),
                        page,
                        size);
        return assembleRawPage(headers, page, size, totalItems);
    }

    @Cacheable(value = CacheConfig.TRIP_FEED_LIKED_BY, key = "T(java.util.List).of(#userId, #page, #size)")
    @Transactional(readOnly = true)
    public TripFeedPageRaw feedLikedByRaw(long userId, int page, int size) {
        List<Long> allLikedTripIds = socialServiceClient.getLikedTripIdsForUser(userId);
        if (allLikedTripIds == null || allLikedTripIds.isEmpty()) {
            return emptyRawPage(page, size);
        }
        long totalItems = allLikedTripIds.size();
        int from = page * size;
        if (from >= allLikedTripIds.size()) {
            return new TripFeedPageRaw(List.of(), page, size, totalItems, totalPages(totalItems, size));
        }
        int to = Math.min(allLikedTripIds.size(), from + size);
        List<Long> pageIds = allLikedTripIds.subList(from, to);
        List<TripHeaderRow> headers =
                entityManager
                        .createQuery(
                                "SELECT new com.tripplanning.trip.read.TripFeedCachedReader$TripHeaderRow("
                                        + "t.id, t.title, t.destination, t.startDate, t.shortDescription,"
                                        + " u.id, u.name, u.imagePath)"
                                        + " FROM TripEntity t JOIN t.user u"
                                        + " WHERE t.id IN :ids"
                                        + " ORDER BY t.id DESC",
                                TripHeaderRow.class)
                        .setParameter("ids", pageIds)
                        .getResultList();
        return assembleRawPage(headers, page, size, totalItems);
    }

    /** Not cached in Redis: detail DTOs embed {@link TripFeedDtos} types that are awkward to round-trip. */
    @Transactional(readOnly = true)
    public TripFeedDetailRaw detailRaw(long tripId) {
        TripDetailHeaderRow header;
        try {
            header =
                    entityManager
                            .createQuery(
                                    "SELECT new com.tripplanning.trip.read.TripFeedCachedReader$TripDetailHeaderRow("
                                            + "t.id, t.title, t.destination, t.destinationGooglePlaceId, t.startDate,"
                                            + " t.shortDescription, t.longDescription,"
                                            + " u.id, u.name, u.imagePath)"
                                            + " FROM TripEntity t JOIN t.user u"
                                            + " WHERE t.id = :id",
                                    TripDetailHeaderRow.class)
                            .setParameter("id", tripId)
                            .getSingleResult();
        } catch (NoResultException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        }

        List<TripStopRow> stopRows =
                entityManager
                        .createQuery(
                                "SELECT new com.tripplanning.trip.read.TripFeedCachedReader$TripStopRow("
                                        + "tl.id, tl.googlePlaceId, tl.placeName, tl.cityName, tl.description, tl.startDate, tl.endDate,"
                                        + " gp.latitude, gp.longitude, gp.countryCode, gp.formattedAddress)"
                                        + " FROM TripLocationEntity tl"
                                        + " LEFT JOIN GooglePlaceEntity gp ON gp.googlePlaceId = tl.googlePlaceId"
                                        + " WHERE tl.trip.id = :id ORDER BY tl.id",
                                TripStopRow.class)
                        .setParameter("id", tripId)
                        .getResultList();

        List<TripAccomRow> accomRows =
                entityManager
                        .createQuery(
                                "SELECT new com.tripplanning.trip.read.TripFeedCachedReader$TripAccomRow("
                                        + "a.id, a.type, a.name, a.address, a.googlePlaceId, gp.cityName, gp.latitude, gp.longitude, gp.countryCode,"
                                        + " a.checkInDate, a.checkOutDate, a.cost, a.currency)"
                                        + " FROM TripEntity t JOIN t.accommodations a"
                                        + " LEFT JOIN GooglePlaceEntity gp ON gp.googlePlaceId = a.googlePlaceId"
                                        + " WHERE t.id = :id ORDER BY a.id",
                                TripAccomRow.class)
                        .setParameter("id", tripId)
                        .getResultList();

        List<TripTransportRow> transportRows =
                entityManager
                        .createQuery(
                                "SELECT new com.tripplanning.trip.read.TripFeedCachedReader$TripTransportRow("
                                        + "tr.id, tr.startGooglePlaceId, tr.endGooglePlaceId, tr.startAddress, tr.endAddress,"
                                        + " gpStart.latitude, gpStart.longitude, gpEnd.latitude, gpEnd.longitude)"
                                        + " FROM TripEntity t JOIN t.transports tr"
                                        + " LEFT JOIN GooglePlaceEntity gpStart ON gpStart.googlePlaceId = tr.startGooglePlaceId"
                                        + " LEFT JOIN GooglePlaceEntity gpEnd ON gpEnd.googlePlaceId = tr.endGooglePlaceId"
                                        + " WHERE t.id = :id ORDER BY tr.id",
                                TripTransportRow.class)
                        .setParameter("id", tripId)
                        .getResultList();

        Map<Long, List<String>> imagePathsByStopId = collectImagePathsByStopId(tripId);

        List<TripFeedDetailStopRaw> stops = new ArrayList<>(stopRows.size());
        for (TripStopRow row : stopRows) {
            stops.add(
                    new TripFeedDetailStopRaw(
                            row.tripLocationId(),
                            row.googlePlaceId(),
                            row.placeName(),
                            row.cityName(),
                            row.description() == null ? "" : row.description(),
                            row.startDate(),
                            row.endDate(),
                            row.latitude(),
                            row.longitude(),
                            row.countryCode(),
                            row.formattedAddress(),
                            imagePathsByStopId.getOrDefault(row.tripLocationId(), List.of())));
        }
        List<TripFeedAccommodation> accommodations = new ArrayList<>(accomRows.size());
        for (TripAccomRow row : accomRows) {
            accommodations.add(
                    new TripFeedAccommodation(
                            row.id(),
                            row.type(),
                            row.name(),
                            row.address(),
                            row.googlePlaceId(),
                            row.cityName(),
                            row.latitude(),
                            row.longitude(),
                            row.countryCode(),
                            row.checkInDate(),
                            row.checkOutDate(),
                            row.cost(),
                            row.currency()));
        }
        List<TripFeedTransport> transports = new ArrayList<>(transportRows.size());
        for (TripTransportRow row : transportRows) {
            transports.add(
                    new TripFeedTransport(
                            row.id(),
                            row.startGooglePlaceId(),
                            row.endGooglePlaceId(),
                            row.startAddress(),
                            row.endAddress(),
                            row.startLatitude(),
                            row.startLongitude(),
                            row.endLatitude(),
                            row.endLongitude()));
        }
        return new TripFeedDetailRaw(
                header.id(),
                header.title(),
                header.destination(),
                header.destinationGooglePlaceId(),
                header.startDate(),
                header.shortDescription(),
                header.longDescription() == null ? "" : header.longDescription(),
                new TripFeedAuthorRaw(header.authorId(), header.authorName(), header.authorImagePath()),
                stops,
                accommodations,
                transports);
    }

    private Map<Long, List<String>> collectImagePathsByStopId(long tripId) {
        Map<Long, List<String>> out = new LinkedHashMap<>();
        for (TripLocationEntity stop : tripLocationRepository.findAllByTripIdWithImages(tripId)) {
            List<String> paths = new ArrayList<>();
            if (stop.getImages() != null) {
                for (TripLocationImageEntity img : stop.getImages()) {
                    if (img.getImagePath() != null && !img.getImagePath().isBlank()) {
                        paths.add(img.getImagePath());
                    }
                }
            }
            out.put(stop.getId(), paths);
        }
        return out;
    }

    private List<TripHeaderRow> queryHeaders(
            String jpql, Map<String, Object> params, int page, int size) {
        TypedQuery<TripHeaderRow> q = entityManager.createQuery(jpql, TripHeaderRow.class);
        for (Map.Entry<String, Object> e : params.entrySet()) {
            q.setParameter(e.getKey(), e.getValue());
        }
        q.setFirstResult(page * size);
        q.setMaxResults(size);
        return q.getResultList();
    }

    private long countAll() {
        return entityManager
                .createQuery("SELECT count(t.id) FROM TripEntity t", Long.class)
                .getSingleResult();
    }

    private long countByUser(long userId) {
        return entityManager
                .createQuery(
                        "SELECT count(t.id) FROM TripEntity t WHERE t.user.id = :userId",
                        Long.class)
                .setParameter("userId", userId)
                .getSingleResult();
    }

    private TripFeedPageRaw assembleRawPage(
            List<TripHeaderRow> headers, int page, int size, long totalItems) {
        if (headers.isEmpty()) {
            return emptyRawPage(page, size, totalItems);
        }
        List<Long> ids = new ArrayList<>(headers.size());
        for (TripHeaderRow row : headers) {
            ids.add(row.id());
        }

        Map<Long, List<String>> locationsByTripId = batchLocationNamesByTripId(ids);
        Map<Long, List<String>> accomNamesByTripId = batchAccommodationNamesByTripId(ids);
        Map<Long, List<String>> transportRoutesByTripId = batchTransportRoutesByTripId(ids);

        List<TripFeedItemRaw> items = new ArrayList<>(headers.size());
        for (TripHeaderRow row : headers) {
            items.add(
                    new TripFeedItemRaw(
                            row.id(),
                            row.title(),
                            row.destination(),
                            row.startDate(),
                            row.shortDescription(),
                            new TripFeedAuthorRaw(row.authorId(), row.authorName(), row.authorImagePath()),
                            locationsByTripId.getOrDefault(row.id(), List.of()),
                            accomNamesByTripId.getOrDefault(row.id(), List.of()),
                            transportRoutesByTripId.getOrDefault(row.id(), List.of())));
        }
        return new TripFeedPageRaw(items, page, size, totalItems, totalPages(totalItems, size));
    }

    private Map<Long, List<String>> batchLocationNamesByTripId(List<Long> tripIds) {
        if (tripIds.isEmpty()) return Map.of();
        List<TripStopNameRow> rows =
                entityManager
                        .createQuery(
                                "SELECT new com.tripplanning.trip.read.TripFeedCachedReader$TripStopNameRow("
                                        + "tl.trip.id, tl.placeName, tl.id)"
                                        + " FROM TripLocationEntity tl"
                                        + " WHERE tl.trip.id IN :ids"
                                        + " ORDER BY tl.trip.id, tl.id",
                                TripStopNameRow.class)
                        .setParameter("ids", tripIds)
                        .getResultList();
        Map<Long, List<String>> out = new LinkedHashMap<>();
        for (TripStopNameRow row : rows) {
            out.computeIfAbsent(row.tripId(), k -> new ArrayList<>()).add(row.placeName());
        }
        return out;
    }

    private Map<Long, List<String>> batchAccommodationNamesByTripId(List<Long> tripIds) {
        if (tripIds.isEmpty()) return Map.of();
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
        if (tripIds.isEmpty()) return Map.of();
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

    private static int totalPages(long totalItems, int pageSize) {
        if (pageSize <= 0) return 0;
        return (int) ((totalItems + pageSize - 1) / pageSize);
    }

    private static TripFeedPageRaw emptyRawPage(int page, int size) {
        return new TripFeedPageRaw(Collections.emptyList(), page, size, 0L, 0);
    }

    private static TripFeedPageRaw emptyRawPage(int page, int size, long totalItems) {
        return new TripFeedPageRaw(
                Collections.emptyList(), page, size, totalItems, totalPages(totalItems, size));
    }

    /**
     * Cached form of the public feed page. Holds {@link TripFeedItemRaw} entries that carry GCS
     * <em>image paths</em> instead of pre-signed URLs.
     */
    public record TripFeedPageRaw(
            List<TripFeedItemRaw> items, int page, int size, long totalItems, int totalPages) {}

    public record TripFeedItemRaw(
            long id,
            String title,
            String destination,
            LocalDate startDate,
            String shortDescription,
            TripFeedAuthorRaw author,
            List<String> locations,
            List<String> accommodationNames,
            List<String> transportRoutes) {}

    public record TripFeedDetailRaw(
            long id,
            String title,
            String destination,
            String destinationGooglePlaceId,
            LocalDate startDate,
            String shortDescription,
            String longDescription,
            TripFeedAuthorRaw author,
            List<TripFeedDetailStopRaw> stops,
            List<TripFeedAccommodation> accommodations,
            List<TripFeedTransport> transports) {}

    public record TripFeedDetailStopRaw(
            long id,
            String googlePlaceId,
            String placeName,
            String cityName,
            String description,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Double latitude,
            Double longitude,
            String countryCode,
            String formattedAddress,
            List<String> imagePaths) {}

    public record TripFeedAuthorRaw(long id, String name, String imagePath) {}

    /**
     * Public so JPQL constructor expressions can resolve the row type via its fully qualified name
     * (Hibernate evaluates {@code new com.tripplanning....TripHeaderRow(...)} via reflection and
     * needs visibility on the inner record).
     */
    public record TripHeaderRow(
            Long id,
            String title,
            String destination,
            LocalDate startDate,
            String shortDescription,
            Long authorId,
            String authorName,
            String authorImagePath) {}

    public record TripDetailHeaderRow(
            Long id,
            String title,
            String destination,
            String destinationGooglePlaceId,
            LocalDate startDate,
            String shortDescription,
            String longDescription,
            Long authorId,
            String authorName,
            String authorImagePath) {}

    public record TripStopRow(
            Long tripLocationId,
            String googlePlaceId,
            String placeName,
            String cityName,
            String description,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Double latitude,
            Double longitude,
            String countryCode,
            String formattedAddress) {}

    public record TripStopNameRow(Long tripId, String placeName, Long tripLocationId) {}

    public record TripAccomRow(
            Long id,
            String type,
            String name,
            String address,
            String googlePlaceId,
            String cityName,
            Double latitude,
            Double longitude,
            String countryCode,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            BigDecimal cost,
            String currency) {}

    public record TripTransportRow(
            Long id,
            String startGooglePlaceId,
            String endGooglePlaceId,
            String startAddress,
            String endAddress,
            Double startLatitude,
            Double startLongitude,
            Double endLatitude,
            Double endLongitude) {}
}
