package com.tripplanning.social;

import com.tripplanning.trip.TripEntity;
import com.tripplanning.trip.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;
    private final TripRepository tripRepository;

    // GET /api/v2/trips/search/countLikes?tripId=1
    @GetMapping("/api/v2/trips/search/countLikes")
    public long countLikes(@RequestParam Long tripId) {
        return likeService.countLikes(tripId);
    }

    // GET /api/v2/trips/search/findByLikedByUsersId?userId=1
    @GetMapping("/api/v2/trips/search/findByLikedByUsersId")
    public Page<TripEntity> findByLikedByUsersId(@RequestParam Long userId, Pageable pageable) {
        Page<TripLikeDocument> likePage = likeService.getLikedTripsByUser(userId, pageable);
        List<Long> tripIds = likePage.getContent().stream()
                .map(TripLikeDocument::getTripId)
                .collect(Collectors.toList());
        List<TripEntity> trips = tripRepository.findAllById(tripIds);
        return new PageImpl<>(trips, pageable, likePage.getTotalElements());
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
            @RequestBody String body) {
        Long tripId = parseIdFromUriOrNumber(body.trim());
        likeService.addLike(userId, tripId);
        return ResponseEntity.noContent().build();
    }

    // DELETE /api/v2/users/{userId}/likedTrips/{tripId}
    @DeleteMapping("/api/v2/users/{userId}/likedTrips/{tripId}")
    public ResponseEntity<Void> unlikeTrip(
            @PathVariable Long userId,
            @PathVariable Long tripId) {
        likeService.removeLike(userId, tripId);
        return ResponseEntity.noContent().build();
    }

    // HEAD /api/v2/users/{userId}/likedTrips/{tripId}  (exists-Check)
    @RequestMapping(method = RequestMethod.HEAD,
                    value = "/api/v2/users/{userId}/likedTrips/{tripId}")
    public ResponseEntity<Void> likeExists(
            @PathVariable Long userId,
            @PathVariable Long tripId) {
        return likeService.existsLike(userId, tripId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    private Long parseIdFromUriOrNumber(String raw) {
        // "/api/v2/trips/42" oder "42"
        String[] parts = raw.split("/");
        return Long.parseLong(parts[parts.length - 1]);
    }
}
