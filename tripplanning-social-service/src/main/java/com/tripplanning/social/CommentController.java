package com.tripplanning.social;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.tripplanning.common.client.TripServiceClient;
import com.tripplanning.social.dto.CommunityDtos.CommunityCommentItem;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v2/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentRepository commentRepository;
    private final TripServiceClient tripServiceClient;
    private final FirestoreSocialService firestoreSocialService;
    private final SocialCommentEnricher socialCommentEnricher;
    private final CommunityCacheEvictor communityCacheEvictor;

    @GetMapping("/search/findByTripIdOrderByCreatedAtDesc")
    public Map<String, Object> getByTrip(
            @RequestParam Long tripId,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String cursor) {
        if (!tripServiceClient.tripExists(tripId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        }
        FirestoreSocialService.CommentPage page =
                firestoreSocialService.fetchCommentPage(tripId, size, cursor);
        long totalElements = firestoreSocialService.countCommentsForTrip(tripId);
        List<CommunityCommentItem> enriched = socialCommentEnricher.enrich(page.items());

        String base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        List<Map<String, Object>> embedded = new ArrayList<>();
        for (CommunityCommentItem c : enriched) {
            embedded.add(toHalComment(c, base));
        }

        var selfBuilder =
                ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/api/v2/comments/search/findByTripIdOrderByCreatedAtDesc")
                        .queryParam("tripId", tripId)
                        .queryParam("size", size);
        if (cursor != null && !cursor.isBlank()) {
            selfBuilder = selfBuilder.queryParam("cursor", cursor);
        }
        String selfHref = selfBuilder.build().toUriString();

        Map<String, Object> pageMeta = new LinkedHashMap<>();
        pageMeta.put("size", embedded.size());
        pageMeta.put("totalElements", totalElements);
        pageMeta.put("totalPages", page.hasMore() ? 2 : 1);
        pageMeta.put("number", 0);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("_embedded", Map.of("comments", embedded));
        body.put("_links", Map.of("self", Map.of("href", selfHref)));
        body.put("page", pageMeta);
        if (page.nextCursor() != null) {
            body.put("nextCursor", page.nextCursor());
        }
        body.put("hasMore", page.hasMore());
        return body;
    }

    private static Map<String, Object> toHalComment(CommunityCommentItem c, String baseUrl) {
        Map<String, Object> links = new LinkedHashMap<>();
        links.put("self", Map.of("href", baseUrl + "/api/v2/comments/" + c.id()));
        links.put("trip", Map.of("href", baseUrl + "/api/v2/trips/" + c.tripId()));
        links.put("user", Map.of("href", baseUrl + "/api/v2/users/" + c.userId()));

        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("content", c.content());
        entity.put("createdAt", c.createdAt());
        entity.put("userName", c.userName());
        entity.put("_links", links);
        return entity;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        Long tripId = resolveTripId(body);
        if (!tripServiceClient.tripExists(tripId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        }
        Long userId = Long.parseLong(jwt.getSubject());
        String content = (String) body.get("content");
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content is required");
        }
        CommentDocument saved =
                commentRepository.save(new CommentDocument(tripId, userId, content)).block();
        communityCacheEvictor.evictForTrip(tripId);
        String base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        String userName =
                tripServiceClient
                        .getUsersByIds(List.of(userId))
                        .getOrDefault(userId, new com.tripplanning.common.internal.InternalUserDto(userId, "traveller"))
                        .name();
        CommunityCommentItem item =
                new CommunityCommentItem(
                        saved.getId(),
                        saved.getTripId(),
                        saved.getUserId(),
                        userName,
                        saved.getContent(),
                        saved.getCreatedAt() != null
                                ? Instant.ofEpochMilli(saved.getCreatedAt()).toString()
                                : "");
        return ResponseEntity.ok(toHalComment(item, base));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {
        long callerId = Long.parseLong(jwt.getSubject());
        CommentDocument comment = commentRepository.findById(id).block();
        if (comment == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Kommentar nicht gefunden");
        }
        boolean isAuthor = comment.getUserId().equals(callerId);
        boolean isTripOwner = tripServiceClient.isTripOwnedBy(comment.getTripId(), callerId);
        if (!isAuthor && !isTripOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nicht berechtigt");
        }
        commentRepository.deleteById(id).block();
        communityCacheEvictor.evictForTrip(comment.getTripId());
        return ResponseEntity.noContent().build();
    }

    private static Long resolveTripId(Map<String, Object> body) {
        if (body.containsKey("tripId")) {
            return parseTripRef(body.get("tripId"));
        }
        if (body.get("trip") != null) {
            return parseTripRef(body.get("trip"));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "trip or tripId is required");
    }

    private static Long parseTripRef(Object val) {
        if (val instanceof Number n) {
            return n.longValue();
        }
        if (val instanceof String s) {
            String[] parts = s.split("/");
            return Long.parseLong(parts[parts.length - 1]);
        }
        if (val instanceof Map<?, ?> map) {
            Object href = map.get("href");
            if (href instanceof String s) {
                return parseTripRef(s);
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid trip reference");
    }
}
