package com.tripplanning.social;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.tripplanning.social.dto.CommunityDtos.CommunityCommentItem;
import com.tripplanning.user.UserEntity;
import com.tripplanning.user.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SocialCommentEnricher {

    private final UserRepository userRepository;

    public List<CommunityCommentItem> enrich(List<FirestoreSocialService.CommentRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = new HashSet<>();
        for (FirestoreSocialService.CommentRow r : rows) {
            ids.add(r.userId());
        }
        List<UserEntity> users = userRepository.findAllById(ids);
        Map<Long, String> nameById =
                users.stream()
                        .collect(Collectors.toMap(UserEntity::getId, UserEntity::getName));
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
