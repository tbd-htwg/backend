package com.tripplanning.social.internal;

import java.util.Comparator;
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
        List<TripLikeDocument> likes =
                tripLikeRepository.findByUserId(userId).collectList().blockOptional().orElse(List.of());
        likes.sort(
                Comparator.comparing(
                                TripLikeDocument::getCreatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(
                                TripLikeDocument::getTripId,
                                Comparator.nullsLast(Comparator.reverseOrder())));
        List<Long> tripIds = likes.stream().map(TripLikeDocument::getTripId).toList();
        return new LikedTripIdsResponse(tripIds);
    }
}
