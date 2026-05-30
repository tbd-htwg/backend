package com.tripplanning.social;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.tripplanning.social.config.SocialCacheConfig;

import lombok.RequiredArgsConstructor;

/**
 * Caches Firestore-heavy community payload (counts + first comment page). User-specific like status
 * is resolved outside the cache in {@link TripCommunityController}.
 */
@Service
@RequiredArgsConstructor
public class CommunityCachedReader {

    private static final int BUNDLE_COMMENT_PAGE = 10;

    private final FirestoreSocialService firestoreSocialService;

    @Cacheable(value = SocialCacheConfig.COMMUNITY_BUNDLE, key = "#tripId")
    public CommunityBundleCache communityBundleRaw(long tripId) {
        long likeCount = firestoreSocialService.countLikesForTrip(tripId);
        long totalCommentCount = firestoreSocialService.countCommentsForTrip(tripId);
        FirestoreSocialService.CommentPage page =
                firestoreSocialService.fetchCommentPage(tripId, BUNDLE_COMMENT_PAGE, null);
        return new CommunityBundleCache(likeCount, totalCommentCount, page);
    }

    public record CommunityBundleCache(
            long likeCount, long totalCommentCount, FirestoreSocialService.CommentPage commentPage) {}
}
