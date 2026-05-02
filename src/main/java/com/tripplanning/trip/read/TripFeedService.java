package com.tripplanning.trip.read;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.tripplanning.images.ImageService;
import com.tripplanning.trip.read.TripFeedCachedReader.TripFeedAuthorRaw;
import com.tripplanning.trip.read.TripFeedCachedReader.TripFeedDetailRaw;
import com.tripplanning.trip.read.TripFeedCachedReader.TripFeedDetailStopRaw;
import com.tripplanning.trip.read.TripFeedCachedReader.TripFeedItemRaw;
import com.tripplanning.trip.read.TripFeedCachedReader.TripFeedPageRaw;
import com.tripplanning.trip.read.TripFeedDtos.TripFeedAuthor;
import com.tripplanning.trip.read.TripFeedDtos.TripFeedDetail;
import com.tripplanning.trip.read.TripFeedDtos.TripFeedDetailStop;
import com.tripplanning.trip.read.TripFeedDtos.TripFeedItem;
import com.tripplanning.trip.read.TripFeedDtos.TripFeedPage;

import lombok.RequiredArgsConstructor;

/**
 * Public read API for the SPA feed and trip detail. Pulls the cached SQL-only payload from
 * {@link TripFeedCachedReader} (where the heavy fixed-count JPQL queries live) and rewrites every
 * GCS image path into a signed URL using the per-request authentication state. Splitting the
 * cached and uncached layers across two beans is necessary so the {@code @Cacheable} aspect on
 * {@link TripFeedCachedReader} actually fires through the Spring proxy.
 */
@Service
@RequiredArgsConstructor
public class TripFeedService {

    private static final int MAX_PAGE_SIZE = 50;

    private final TripFeedCachedReader cachedReader;
    private final ImageService imageService;

    public TripFeedPage<TripFeedItem> feed(int page, int size) {
        return materialise(cachedReader.feedRaw(safePage(page), safeSize(size)));
    }

    public TripFeedPage<TripFeedItem> feedByUser(long userId, int page, int size) {
        return materialise(cachedReader.feedByUserRaw(userId, safePage(page), safeSize(size)));
    }

    public TripFeedPage<TripFeedItem> feedLikedBy(long userId, int page, int size) {
        return materialise(cachedReader.feedLikedByRaw(userId, safePage(page), safeSize(size)));
    }

    public TripFeedDetail detail(long tripId) {
        return materialiseDetail(cachedReader.detailRaw(tripId));
    }

    public boolean tripExists(long tripId) {
        return cachedReader.tripExists(tripId);
    }

    private TripFeedPage<TripFeedItem> materialise(TripFeedPageRaw raw) {
        List<TripFeedItem> items = new ArrayList<>(raw.items().size());
        for (TripFeedItemRaw item : raw.items()) {
            items.add(
                    new TripFeedItem(
                            item.id(),
                            item.title(),
                            item.destination(),
                            item.startDate(),
                            item.shortDescription(),
                            materialiseAuthor(item.author()),
                            item.locations(),
                            item.accommodationNames(),
                            item.transportTypes()));
        }
        return new TripFeedPage<>(items, raw.page(), raw.size(), raw.totalItems(), raw.totalPages());
    }

    private TripFeedDetail materialiseDetail(TripFeedDetailRaw raw) {
        List<TripFeedDetailStop> stops = new ArrayList<>(raw.stops().size());
        for (TripFeedDetailStopRaw stop : raw.stops()) {
            List<String> signed = new ArrayList<>(stop.imagePaths().size());
            for (String path : stop.imagePaths()) {
                String url = imageService.createSignedReadUrlIfAuthenticated(path);
                if (url != null && !url.isBlank()) {
                    signed.add(url);
                }
            }
            stops.add(
                    new TripFeedDetailStop(
                            stop.id(),
                            stop.locationId(),
                            stop.locationName(),
                            stop.description(),
                            stop.startDate(),
                            stop.endDate(),
                            signed));
        }
        return new TripFeedDetail(
                raw.id(),
                raw.title(),
                raw.destination(),
                raw.startDate(),
                raw.shortDescription(),
                raw.longDescription(),
                materialiseAuthor(raw.author()),
                stops,
                raw.accommodations(),
                raw.transports());
    }

    private TripFeedAuthor materialiseAuthor(TripFeedAuthorRaw raw) {
        return new TripFeedAuthor(
                raw.id(), raw.name(), imageService.createSignedReadUrlIfAuthenticated(raw.imagePath()));
    }

    private static int safePage(int page) {
        return Math.max(0, page);
    }

    private static int safeSize(int requested) {
        if (requested < 1) return 10;
        return Math.min(requested, MAX_PAGE_SIZE);
    }
}
