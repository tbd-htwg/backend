package com.tripplanning.social.dto;

import java.util.List;

public final class CommunityDtos {
    private CommunityDtos() {}

    public record TripCommunityResponse(
            long likeCount,
            long totalCommentCount,
            Boolean likedByCurrentUser,
            List<CommunityCommentItem> comments,
            String commentsNextCursor,
            boolean hasMoreComments) {}

    public record TripCommentsPageResponse(
            List<CommunityCommentItem> items, String nextCursor, boolean hasMore) {}

    public record CommunityCommentItem(
            String id,
            long tripId,
            long userId,
            String userName,
            String content,
            String createdAt) {}
}
