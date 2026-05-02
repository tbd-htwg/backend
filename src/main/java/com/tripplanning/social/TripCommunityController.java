package com.tripplanning.social;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.tripplanning.social.dto.CommunityDtos.CommunityCommentItem;
import com.tripplanning.social.dto.CommunityDtos.TripCommentsPageResponse;
import com.tripplanning.social.dto.CommunityDtos.TripCommunityResponse;
import com.tripplanning.trip.TripRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v2/trips")
@RequiredArgsConstructor
public class TripCommunityController {

    private static final int BUNDLE_COMMENT_PAGE = 10;

    private final TripRepository tripRepository;
    private final TripLikeRepository tripLikeRepository;
    private final FirestoreSocialService firestoreSocialService;
    private final SocialCommentEnricher socialCommentEnricher;

    /**
     * Bundled community payload for the trip detail page: counts, optional like status for the
     * current user, and the first page of comments with display names (no N+1 user fetches from the
     * SPA).
     */
    @GetMapping("/{tripId}/community")
    public TripCommunityResponse getCommunity(
            @PathVariable Long tripId,
            @AuthenticationPrincipal Jwt jwt) {
        requireTrip(tripId);
        long likeCount = firestoreSocialService.countLikesForTrip(tripId);
        long totalCommentCount = firestoreSocialService.countCommentsForTrip(tripId);
        Boolean likedByCurrentUser = null;
        if (jwt != null) {
            long uid = Long.parseLong(jwt.getSubject());
            likedByCurrentUser =
                    Boolean.TRUE.equals(
                            tripLikeRepository
                                    .findByUserIdAndTripId(uid, tripId)
                                    .map(d -> true)
                                    .defaultIfEmpty(false)
                                    .block());
        }
        FirestoreSocialService.CommentPage page =
                firestoreSocialService.fetchCommentPage(tripId, BUNDLE_COMMENT_PAGE, null);
        List<CommunityCommentItem> comments = socialCommentEnricher.enrich(page.items());
        return new TripCommunityResponse(
                likeCount,
                totalCommentCount,
                likedByCurrentUser,
                comments,
                page.nextCursor(),
                page.hasMore());
    }

    /** Cursor-based pagination for comments (newest first). */
    @GetMapping("/{tripId}/comments")
    public TripCommentsPageResponse listComments(
            @PathVariable Long tripId,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String cursor) {
        requireTrip(tripId);
        FirestoreSocialService.CommentPage page =
                firestoreSocialService.fetchCommentPage(tripId, pageSize, cursor);
        return new TripCommentsPageResponse(
                socialCommentEnricher.enrich(page.items()), page.nextCursor(), page.hasMore());
    }

    private void requireTrip(Long tripId) {
        if (!tripRepository.existsById(tripId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        }
    }
}
