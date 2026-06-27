package com.tripplanning.trip.read;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.tripplanning.common.client.SocialServiceClient;
import com.tripplanning.images.ImageService;
import com.tripplanning.images.TripFeedLocationImagesHelper;
import com.tripplanning.search.TripSearchDto;
import com.tripplanning.search.TripSimilarityPort;
import com.tripplanning.trip.TripRepository;
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
import lombok.extern.slf4j.Slf4j;

/**
 * Public read API for the SPA feed and trip detail. Pulls the cached SQL-only payload from
 * {@link TripFeedCachedReader} (where the heavy fixed-count JPQL queries live) and rewrites every
 * GCS image path into a signed URL using the per-request authentication state. Splitting the
 * cached and uncached layers across two beans is necessary so the {@code @Cacheable} aspect on
 * {@link TripFeedCachedReader} actually fires through the Spring proxy.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TripFeedService {

    static final String SOURCE_LATEST = "latest";
    static final String SOURCE_RECOMMENDED = "recommended";
    static final String SOURCE_LATEST_FALLBACK = "latest-fallback";

    private static final int MAX_PAGE_SIZE = 50;
    /** Maximum number of reference trips fed into the MLT query. */
    private static final int MAX_REFERENCE_TRIPS = 5;

    private final TripFeedCachedReader cachedReader;
    private final ImageService imageService;
    private final TripFeedLocationImagesHelper tripFeedLocationImagesHelper;
    private final TripRepository tripRepository;
    private final SocialServiceClient socialServiceClient;
    private final TripSimilarityPort tripSimilarityPort;

    public TripFeedPage<TripFeedItem> feed(int page, int size, Authentication authentication) {
        TripAccessHelper.requirePublicTripAccessOrAuth(authentication);
        return materialise(cachedReader.feedRaw(safePage(page), safeSize(size)), SOURCE_LATEST);
    }

    public TripFeedPage<TripFeedItem> feedByUser(
            long userId, int page, int size, Authentication authentication) {
        TripAccessHelper.requirePublicTripAccessOrAuth(authentication);
        Long viewerUserId = TripAccessHelper.viewerUserId(authentication);
        boolean includeHidden = viewerUserId != null && viewerUserId == userId;
        return materialise(
                cachedReader.feedByUserRaw(userId, includeHidden, safePage(page), safeSize(size)));
    }

    public TripFeedPage<TripFeedItem> feedLikedBy(
            long userId, int page, int size, Authentication authentication) {
        TripAccessHelper.requirePublicTripAccessOrAuth(authentication);
        return materialise(cachedReader.feedLikedByRaw(userId, safePage(page), safeSize(size)));
    }

    public TripFeedDetail detail(long tripId, Authentication authentication) {
        TripAccessHelper.requirePublicTripAccessOrAuth(authentication);
        Long viewerUserId = TripAccessHelper.viewerUserId(authentication);
        TripFeedDetailRaw raw = cachedReader.detailRaw(tripId);
        TripAccessHelper.assertTripReadable(raw, viewerUserId);
        return materialiseDetail(raw);
    }

    public boolean tripExists(long tripId) {
        return cachedReader.tripExists(tripId);
    }

    /**
     * Returns a personalised feed based on Elasticsearch "More Like This".
     * Uses the user's liked trips (preferred) or own trips as similarity anchors.
     * Falls back to the chronological {@link #feed} when the user has no anchors or MLT is empty.
     *
     * @param userId the authenticated user's ID
     * @param page   zero-based page index
     * @param size   page size
     */
    public TripFeedPage<TripFeedItem> recommendedFeed(
            long userId, int page, int size, Authentication authentication) {
        TripAccessHelper.requirePublicTripAccessOrAuth(authentication);
        List<Long> likedIds = socialServiceClient.getLikedTripIdsForUser(userId);
        if (likedIds == null) {
            likedIds = List.of();
        }

        List<Long> recentOwnIds =
                tripRepository
                        .findByUserId(
                                userId,
                                PageRequest.of(
                                        0, MAX_REFERENCE_TRIPS, Sort.by(Sort.Direction.DESC, "id")))
                        .stream()
                        .map(t -> t.getId())
                        .collect(Collectors.toList());

        List<Long> allOwnIds =
                tripRepository.findByUserId(userId, Pageable.unpaged()).stream()
                        .map(t -> t.getId())
                        .collect(Collectors.toList());

        Set<Long> excludeIds = new LinkedHashSet<>();
        excludeIds.addAll(likedIds);
        excludeIds.addAll(allOwnIds);

        List<Long> referenceIds = new ArrayList<>();
        if (!likedIds.isEmpty()) {
            for (Long id : likedIds) {
                if (referenceIds.size() >= MAX_REFERENCE_TRIPS) {
                    break;
                }
                referenceIds.add(id);
            }
        } else {
            referenceIds.addAll(recentOwnIds);
        }

        if (referenceIds.isEmpty()) {
            log.info(
                    "recommendedFeed userId={} referenceIds=[] mltTotalHits=0 fallback=true reason=no-anchors",
                    userId);
            return withSource(feed(page, size, authentication), SOURCE_LATEST_FALLBACK);
        }

        List<Long> excludeList = new ArrayList<>(excludeIds);
        Page<TripSearchDto> similar =
                tripSimilarityPort.findSimilar(
                        referenceIds, excludeList, safePage(page), safeSize(size));

        if (similar.isEmpty()) {
            log.info(
                    "recommendedFeed userId={} referenceIds={} mltTotalHits=0 fallback=true",
                    userId,
                    referenceIds);
            return withSource(feed(page, size, authentication), SOURCE_LATEST_FALLBACK);
        }

        log.info(
                "recommendedFeed userId={} referenceIds={} mltTotalHits={} fallback=false",
                userId,
                referenceIds,
                similar.getTotalElements());

        List<TripFeedItem> items =
                withHasLocationImages(
                        similar.getContent().stream()
                                .map(this::searchDtoToFeedItem)
                                .collect(Collectors.toList()));

        return new TripFeedPage<>(
                items,
                similar.getNumber(),
                similar.getSize(),
                similar.getTotalElements(),
                similar.getTotalPages(),
                SOURCE_RECOMMENDED);
    }

    private static TripFeedPage<TripFeedItem> withSource(
            TripFeedPage<TripFeedItem> page, String source) {
        return new TripFeedPage<>(
                page.items(),
                page.page(),
                page.size(),
                page.totalItems(),
                page.totalPages(),
                source);
    }

    /** Converts a {@link TripSearchDto} (from MLT) into a {@link TripFeedItem}. */
    private TripFeedItem searchDtoToFeedItem(TripSearchDto dto) {
        // Author profile image is not available from the search index;
        // pass null – the SPA must handle a missing profileImageUrl gracefully.
        TripFeedAuthor author =
                new TripFeedAuthor(
                        dto.getUserId() != null ? dto.getUserId() : 0L,
                        dto.getAuthor(),
                        null);
        return new TripFeedItem(
                dto.getId(),
                dto.getTitle(),
                dto.getDestination(),
                dto.getStartDate(),
                dto.getShortDescription(),
                author,
                dto.getLocations() != null ? dto.getLocations() : List.of(),
                dto.getAccommodationNames() != null ? dto.getAccommodationNames() : List.of(),
                dto.getTransportRoutes() != null ? dto.getTransportRoutes() : List.of(),
                Boolean.TRUE.equals(dto.getHasLocationImages()),
                true);
    }

    private TripFeedPage<TripFeedItem> materialise(TripFeedPageRaw raw) {
        return materialise(raw, null);
    }

    private TripFeedPage<TripFeedItem> materialise(TripFeedPageRaw raw, String source) {
        List<TripFeedItemRaw> rawItems = raw.items();
        List<String> authorPaths = new ArrayList<>(rawItems.size());
        for (TripFeedItemRaw item : rawItems) {
            authorPaths.add(item.author().imagePath());
        }
        List<String> signedAuthorUrls = signPathsInOrder(authorPaths);

        List<TripFeedItem> items = withHasLocationImages(buildItems(rawItems, signedAuthorUrls));
        return new TripFeedPage<>(items, raw.page(), raw.size(), raw.totalItems(), raw.totalPages(), source);
    }

    private List<TripFeedItem> buildItems(List<TripFeedItemRaw> rawItems, List<String> signedAuthorUrls) {
        List<TripFeedItem> items = new ArrayList<>(rawItems.size());
        for (int i = 0; i < rawItems.size(); i++) {
            TripFeedItemRaw item = rawItems.get(i);
            items.add(
                    new TripFeedItem(
                            item.id(),
                            item.title(),
                            item.destination(),
                            item.startDate(),
                            item.shortDescription(),
                            new TripFeedAuthor(
                                    item.author().id(),
                                    item.author().name(),
                                    signedAuthorUrls.get(i)),
                            item.locations(),
                            item.accommodationNames(),
                            item.transportRoutes(),
                            false,
                            item.visible()));
        }
        return items;
    }

    private List<TripFeedItem> withHasLocationImages(List<TripFeedItem> items) {
        if (items.isEmpty()) {
            return items;
        }
        List<Long> ids = items.stream().map(TripFeedItem::id).toList();
        Set<Long> withImages = tripFeedLocationImagesHelper.tripIdsWithLocationImages(ids);
        return items.stream()
                .map(
                        item ->
                                new TripFeedItem(
                                        item.id(),
                                        item.title(),
                                        item.destination(),
                                        item.startDate(),
                                        item.shortDescription(),
                                        item.author(),
                                        item.locations(),
                                        item.accommodationNames(),
                                        item.transportRoutes(),
                                        withImages.contains(item.id()),
                                        item.visible()))
                .toList();
    }

    private TripFeedDetail materialiseDetail(TripFeedDetailRaw raw) {
        List<String> flatPaths = new ArrayList<>();
        flatPaths.add(raw.author().imagePath());
        List<Integer> pathsPerStop = new ArrayList<>(raw.stops().size());
        for (TripFeedDetailStopRaw stop : raw.stops()) {
            pathsPerStop.add(stop.imagePaths().size());
            flatPaths.addAll(stop.imagePaths());
        }

        List<String> signed = signPathsInOrder(flatPaths);
        int idx = 0;
        String authorUrl = signed.get(idx++);

        List<TripFeedDetailStop> stops = new ArrayList<>(raw.stops().size());
        for (int s = 0; s < raw.stops().size(); s++) {
            TripFeedDetailStopRaw stop = raw.stops().get(s);
            List<String> stopSigned = new ArrayList<>(pathsPerStop.get(s));
            for (int p = 0; p < pathsPerStop.get(s); p++) {
                String url = signed.get(idx++);
                if (url != null && !url.isBlank()) {
                    stopSigned.add(url);
                }
            }
            stops.add(
                    new TripFeedDetailStop(
                            stop.id(),
                            stop.googlePlaceId(),
                            stop.placeName(),
                            stop.cityName(),
                            stop.description(),
                            stop.startDate(),
                            stop.endDate(),
                            stop.latitude(),
                            stop.longitude(),
                            stop.countryCode(),
                            stop.formattedAddress(),
                            stopSigned));
        }
        return new TripFeedDetail(
                raw.id(),
                raw.title(),
                raw.destination(),
                raw.destinationGooglePlaceId(),
                raw.startDate(),
                raw.shortDescription(),
                raw.longDescription(),
                raw.visible(),
                new TripFeedAuthor(raw.author().id(), raw.author().name(), authorUrl),
                stops,
                raw.accommodations(),
                raw.transports());
    }

    /** Signs paths in parallel (bounded pool); preserves input order with nulls for unsigned entries. */
    private List<String> signPathsInOrder(List<String> paths) {
        if (paths.isEmpty()) {
            return List.of();
        }
        return imageService.createSignedReadUrlsIfAuthenticated(paths);
    }

    private static int safePage(int page) {
        return Math.max(0, page);
    }

    private static int safeSize(int requested) {
        if (requested < 1) return 10;
        return Math.min(requested, MAX_PAGE_SIZE);
    }
}
