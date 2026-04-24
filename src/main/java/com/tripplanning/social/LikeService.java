package com.tripplanning.social;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LikeService {

    private final TripLikeMongoRepository likeRepository;

    public boolean existsLike(Long userId, Long tripId) {
        return likeRepository.existsByUserIdAndTripId(userId, tripId);
    }

    public long countLikes(Long tripId) {
        return likeRepository.countByTripId(tripId);
    }

    public void addLike(Long userId, Long tripId) {
        if (!existsLike(userId, tripId)) {
            likeRepository.save(new TripLikeDocument(userId, tripId));
        }
    }

    public void removeLike(Long userId, Long tripId) {
        likeRepository.deleteByUserIdAndTripId(userId, tripId);
    }

    public Page<TripLikeDocument> getLikedTripsByUser(Long userId, Pageable pageable) {
        return likeRepository.findByUserId(userId, pageable);
    }
}
