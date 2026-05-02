package com.tripplanning.social;

import com.tripplanning.trip.TripEntity;
import com.tripplanning.trip.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class LikeController {

    private final TripLikeRepository likeRepository;
    private final TripRepository tripRepository;

    public record CurrentUserLikeStatus(boolean liked) {}

    /**
     * Authenticated membership check for the current user. Returns 200 + JSON in both cases so
     * clients and browser devtools do not treat "not liked" as a failed request (unlike 404).
     */
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

    // GET /api/v2/trips/search/countLikes?tripId=1
    @GetMapping("/api/v2/trips/search/countLikes")
    public long countLikes(@RequestParam Long tripId) {
        return likeRepository.findByTripId(tripId).count().blockOptional().orElse(0L);
    }

    // GET /api/v2/trips/search/findByLikedByUsersId?userId=1
    @GetMapping("/api/v2/trips/search/findByLikedByUsersId")
    public Page<TripEntity> findByLikedByUsersId(@RequestParam Long userId, Pageable pageable) {
        List<Long> tripIds = likeRepository.findByUserId(userId)
                .map(TripLikeDocument::getTripId)
                .collectList()
                .block();
        if (tripIds == null || tripIds.isEmpty()) {
            return Page.empty(pageable);
        }
        List<TripEntity> trips = tripRepository.findAllById(tripIds);
        return new PageImpl<>(trips, pageable, trips.size());
    }

    // GET /api/v2/users/{userId}/likedTrips
    @GetMapping("/api/v2/users/{userId}/likedTrips")
    public Page<TripEntity> getLikedTrips(@PathVariable Long userId, Pageable pageable) {
        return findByLikedByUsersId(userId, pageable);
    }

    // POST /api/v2/users/{userId}/likedTrips
    // Body: text/uri-list oder JSON { "tripId": 1 }
    @PostMapping(value = "/api/v2/users/{userId}/likedTrips",
                 consumes = {"text/uri-list", "application/json"})
    public ResponseEntity<Void> likeTrip(
            @PathVariable Long userId,
            @RequestBody String body,
            @AuthenticationPrincipal Jwt jwt) {
        requireSelf(userId, jwt);
        Long tripId = parseIdFromUriOrNumber(body.trim());
        boolean alreadyLiked = Boolean.TRUE.equals(
                likeRepository.findByUserIdAndTripId(userId, tripId)
                        .map(d -> true)
                        .defaultIfEmpty(false)
                        .block());
        if (!alreadyLiked) {
            likeRepository.save(new TripLikeDocument(userId, tripId)).block();
        }
        return ResponseEntity.noContent().build();
    }

    // DELETE /api/v2/users/{userId}/likedTrips/{tripId}
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
        return ResponseEntity.noContent().build();
    }

    // HEAD /api/v2/users/{userId}/likedTrips/{tripId} (exists-Check)
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
