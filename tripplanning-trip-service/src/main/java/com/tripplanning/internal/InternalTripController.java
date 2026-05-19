package com.tripplanning.internal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tripplanning.common.internal.InternalUserDto;
import com.tripplanning.trip.TripRepository;
import com.tripplanning.trip.read.TripFeedCachedReader;
import com.tripplanning.user.UserRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalTripController {

    private final TripFeedCachedReader tripFeedCachedReader;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;

    @RequestMapping(method = org.springframework.web.bind.annotation.RequestMethod.HEAD, path = "/trips/{id}")
    public ResponseEntity<Void> tripExists(@PathVariable("id") long tripId) {
        return tripFeedCachedReader.tripExists(tripId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @GetMapping("/trips/{id}/owner-user-id")
    public ResponseEntity<Long> tripOwnerUserId(@PathVariable("id") long tripId) {
        return tripRepository
                .findById(tripId)
                .map(t -> ResponseEntity.ok(t.getUser().getId()))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/users")
    public InternalUserDto[] usersByIds(@RequestParam("ids") String idsParam) {
        if (idsParam == null || idsParam.isBlank()) {
            return new InternalUserDto[0];
        }
        var ids =
                java.util.Arrays.stream(idsParam.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(Long::parseLong)
                        .toList();
        return userRepository.findAllById(ids).stream()
                .map(u -> new InternalUserDto(u.getId(), u.getName()))
                .toArray(InternalUserDto[]::new);
    }
}
