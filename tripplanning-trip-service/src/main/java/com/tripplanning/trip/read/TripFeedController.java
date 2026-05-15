package com.tripplanning.trip.read;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tripplanning.trip.read.TripFeedDtos.TripFeedDetail;
import com.tripplanning.trip.read.TripFeedDtos.TripFeedItem;
import com.tripplanning.trip.read.TripFeedDtos.TripFeedPage;

import lombok.RequiredArgsConstructor;

/**
 * Replacement for the Spring Data REST projection-based feed and trip-detail reads. Each endpoint
 * returns a stable JSON shape (no HAL envelopes) and is backed by {@link TripFeedService}, which
 * issues a small fixed number of JPQL queries instead of the N+1 storm caused by SpEL projections
 * walking lazy associations.
 *
 * <p>Routes live under {@code /api/v2/trips/...} so the {@link
 * com.tripplanning.api.config.SecurityConfig} catch-all rule for {@code GET /api/v2/**} keeps them
 * publicly readable, matching the previous {@code ?projection=...} endpoints.
 */
@RestController
@RequestMapping("/api/v2/trips")
@RequiredArgsConstructor
public class TripFeedController {

    private final TripFeedService tripFeedService;

    @GetMapping("/feed")
    public TripFeedPage<TripFeedItem> feed(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return tripFeedService.feed(page, size);
    }

    @GetMapping("/feed/by-user")
    public TripFeedPage<TripFeedItem> feedByUser(
            @RequestParam("userId") long userId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return tripFeedService.feedByUser(userId, page, size);
    }

    @GetMapping("/feed/liked-by")
    public TripFeedPage<TripFeedItem> feedLikedBy(
            @RequestParam("userId") long userId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return tripFeedService.feedLikedBy(userId, page, size);
    }

    @GetMapping("/{id}/detail")
    public TripFeedDetail detail(@PathVariable("id") long id) {
        return tripFeedService.detail(id);
    }
}
