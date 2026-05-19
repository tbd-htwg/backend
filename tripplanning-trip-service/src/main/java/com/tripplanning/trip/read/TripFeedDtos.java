package com.tripplanning.trip.read;

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
            List<String> transportTypes) {}

    /** Trip detail response: trip header, author, stops with image URLs, accommodations, transports. */
    public record TripFeedDetail(
            long id,
            String title,
            String destination,
            LocalDate startDate,
            String shortDescription,
            String longDescription,
            TripFeedAuthor author,
            List<TripFeedDetailStop> stops,
            List<TripFeedAccommodation> accommodations,
            List<TripFeedTransport> transports) {}

    public record TripFeedDetailStop(
            long id,
            long locationId,
            String locationName,
            String description,
            LocalDateTime startDate,
            LocalDateTime endDate,
            List<String> imageUrls) {}

    public record TripFeedAccommodation(long id, String type, String name, String address) {}

    public record TripFeedTransport(long id, String type) {}

    /**
     * Page envelope. Matches Spring's {@code Page} fields enough for a trivial frontend mapping but
     * uses 0-based {@code page}/{@code size}/{@code totalItems}/{@code totalPages} explicitly so it
     * does not depend on the deprecated {@code PageImpl} JSON shape and stays cache-friendly as a
     * record.
     */
    public record TripFeedPage<T>(
            List<T> items, int page, int size, long totalItems, int totalPages) {}
}
