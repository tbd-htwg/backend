package com.tripplanning.social.internal;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripplanning.common.internal.LikedTripIdsResponse;
import com.tripplanning.social.TripLikeDocument;
import com.tripplanning.social.TripLikeRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalSocialController {

    private final TripLikeRepository tripLikeRepository;

    @GetMapping("/users/{userId}/liked-trip-ids")
    public LikedTripIdsResponse likedTripIds(@PathVariable long userId) {
        List<Long> tripIds =
                tripLikeRepository
                        .findByUserId(userId)
                        .map(TripLikeDocument::getTripId)
                        .collectList()
                        .block();
        if (tripIds == null) {
            tripIds = List.of();
        }
        return new LikedTripIdsResponse(tripIds);
    }
}
