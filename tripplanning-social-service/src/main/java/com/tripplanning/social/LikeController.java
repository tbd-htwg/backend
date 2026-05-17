package com.tripplanning.social;

import com.tripplanning.common.client.TripServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class LikeController {

    private final TripLikeRepository likeRepository;
    private final FirestoreSocialService firestoreSocialService;
    private final TripServiceClient tripServiceClient;

    public record CurrentUserLikeStatus(boolean liked) {}

    @GetMapping("/api/v2/trips/{tripId}/liked-by-current-user")
    public CurrentUserLikeStatus likedByCurrentUser(
            @PathVariable Long tripId,
            @AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getSubject());
        boolean exists =
                Boolean.TRUE.equals(
                        likeRepository
                                .findByUserIdAndTripId(userId, tripId)
                                .map(d -> true)
                                .defaultIfEmpty(false)
                                .block());
        return new CurrentUserLikeStatus(exists);
    }

    @GetMapping("/api/v2/trips/search/countLikes")
    public long countLikes(@RequestParam Long tripId) {
        return firestoreSocialService.countLikesForTrip(tripId);
    }

    /**
     * Preferred like endpoint for edge gateways that cannot route POST vs GET on
     * {@code /api/v2/users/{userId}/likedTrips} (GET list stays on trip-service).
     */
    @PostMapping("/api/v2/trips/{tripId}/like")
    public ResponseEntity<Void> likeTripForCurrentUser(
            @PathVariable Long tripId, @AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getSubject());
        return likeTripInternal(userId, tripId);
    }

    @PostMapping(value = "/api/v2/users/{userId}/likedTrips",
                 consumes = {"text/uri-list", "application/json"})
    public ResponseEntity<Void> likeTrip(
            @PathVariable Long userId,
            @RequestBody String body,
            @AuthenticationPrincipal Jwt jwt) {
        requireSelf(userId, jwt);
        Long tripId = parseIdFromUriOrNumber(body.trim());
        return likeTripInternal(userId, tripId);
    }

    private ResponseEntity<Void> likeTripInternal(Long userId, Long tripId) {
        boolean alreadyLiked =
                Boolean.TRUE.equals(
                        likeRepository
                                .findByUserIdAndTripId(userId, tripId)
                                .map(d -> true)
                                .defaultIfEmpty(false)
                                .block());
        if (!alreadyLiked) {
            likeRepository.save(new TripLikeDocument(userId, tripId)).block();
            tripServiceClient.evictLikedByFeedCache();
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/v2/users/{userId}/likedTrips/{tripId}")
    public ResponseEntity<Void> unlikeTrip(
            @PathVariable Long userId,
            @PathVariable Long tripId,
            @AuthenticationPrincipal Jwt jwt) {
        requireSelf(userId, jwt);
        String deterministicId = TripLikeDocument.documentId(userId, tripId);
        likeRepository
                .deleteById(deterministicId)
                .then(likeRepository.deleteByUserIdAndTripId(userId, tripId))
                .block();
        tripServiceClient.evictLikedByFeedCache();
        return ResponseEntity.noContent().build();
    }

    @RequestMapping(method = RequestMethod.HEAD,
                    value = "/api/v2/users/{userId}/likedTrips/{tripId}")
    public ResponseEntity<Void> likeExists(
            @PathVariable Long userId,
            @PathVariable Long tripId) {
        boolean exists = Boolean.TRUE.equals(
                likeRepository.findByUserIdAndTripId(userId, tripId)
                        .map(d -> true)
                        .defaultIfEmpty(false)
                        .block());
        return exists
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    private Long parseIdFromUriOrNumber(String raw) {
        String[] parts = raw.split("/");
        return Long.parseLong(parts[parts.length - 1]);
    }

    private static void requireSelf(Long userId, Jwt jwt) {
        long callerId = Long.parseLong(jwt.getSubject());
        if (callerId != userId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nicht berechtigt");
        }
    }
}
