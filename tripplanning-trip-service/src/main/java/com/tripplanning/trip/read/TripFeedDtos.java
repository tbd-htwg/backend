package com.tripplanning.trip.read;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Stable JSON shapes returned by {@link TripFeedController}. These records replace the Spring Data
 * REST projections (`listFast`, `fullDetailFast`, `withImages`) for the hot read paths and are
 * assembled by a fixed number of JPQL queries inside {@link TripFeedService}, so a single feed page
 * or trip detail response no longer fans out into N+1 round trips against PostgreSQL.
 */
public final class TripFeedDtos {

    private TripFeedDtos() {}

    /** Author block embedded in feed items and detail responses. */
    public record TripFeedAuthor(long id, String name, String profileImageUrl) {}

    /** Single feed/list card: trip header plus the materialised name lists. */
    public record TripFeedItem(
            long id,
            String title,
            String destination,
            LocalDate startDate,
            String shortDescription,
            TripFeedAuthor author,
            List<String> locations,
            List<String> accommodationNames,
            List<String> transportRoutes,
            boolean hasLocationImages,
            boolean visible) {}

    /** Trip detail response: trip header, author, stops with image URLs, accommodations, transports. */
    public record TripFeedDetail(
            long id,
            String title,
            String destination,
            String destinationGooglePlaceId,
            LocalDate startDate,
            String shortDescription,
            String longDescription,
            boolean visible,
            TripFeedAuthor author,
            List<TripFeedDetailStop> stops,
            List<TripFeedAccommodation> accommodations,
            List<TripFeedTransport> transports) {}

    public record TripFeedDetailStop(
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
            List<String> imageUrls) {}

    /** Authenticated trip-detail second stage: persisted image id plus signed read URL. */
    public record TripLocationImageRead(long id, String signedReadUrl) {}

    public record TripFeedAccommodation(
            long id,
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

    public record TripFeedTransport(
            long id,
            String startGooglePlaceId,
            String endGooglePlaceId,
            String startAddress,
            String endAddress,
            Double startLatitude,
            Double startLongitude,
            Double endLatitude,
            Double endLongitude) {}

    /**
     * Page envelope. Matches Spring's {@code Page} fields enough for a trivial frontend mapping but
     * uses 0-based {@code page}/{@code size}/{@code totalItems}/{@code totalPages} explicitly so it
     * does not depend on the deprecated {@code PageImpl} JSON shape and stays cache-friendly as a
     * record.
     *
     * <p>{@code source} is set on feed endpoints: {@code latest}, {@code recommended}, or
     * {@code latest-fallback} when personalised ranking had no anchors or empty MLT results.
     */
    public record TripFeedPage<T>(
            List<T> items, int page, int size, long totalItems, int totalPages, String source) {

        /** Backward-compatible constructor when feed source is not applicable. */
        public TripFeedPage(List<T> items, int page, int size, long totalItems, int totalPages) {
            this(items, page, size, totalItems, totalPages, null);
        }
    }
}
