package com.tripplanning.social;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.tripplanning.social.dto.CommunityDtos.CommunityCommentItem;
import com.tripplanning.common.client.TripServiceClient;
import com.tripplanning.common.internal.InternalUserDto;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SocialCommentEnricher {

    private final TripServiceClient tripServiceClient;

    public List<CommunityCommentItem> enrich(List<FirestoreSocialService.CommentRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = new HashSet<>();
        for (FirestoreSocialService.CommentRow r : rows) {
            ids.add(r.userId());
        }
        Map<Long, InternalUserDto> users = tripServiceClient.getUsersByIds(ids);
        Map<Long, String> nameById =
                users.values().stream()
                        .collect(Collectors.toMap(InternalUserDto::id, InternalUserDto::name));
        List<CommunityCommentItem> out = new ArrayList<>(rows.size());
        for (FirestoreSocialService.CommentRow r : rows) {
            String name = nameById.getOrDefault(r.userId(), "traveller");
            String createdAt =
                    r.createdAtMillis() > 0
                            ? Instant.ofEpochMilli(r.createdAtMillis()).toString()
                            : "";
            out.add(
                    new CommunityCommentItem(
                            r.id(), r.tripId(), r.userId(), name, r.content(), createdAt));
        }
        return out;
    }
}
