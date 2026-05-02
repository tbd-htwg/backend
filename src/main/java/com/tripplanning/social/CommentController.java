package com.tripplanning.social;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.tripplanning.trip.TripRepository;

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
    private final TripRepository tripRepository;

    // GET /api/v2/comments/search/findByTripIdOrderByCreatedAtDesc?tripId=1
    @GetMapping("/search/findByTripIdOrderByCreatedAtDesc")
    public Map<String, Object> getByTrip(@RequestParam Long tripId) {
        List<CommentDocument> list =
                commentRepository.findByTripId(tripId).collectList().block();
        if (list == null) {
            list = List.of();
        } else {
            list = new ArrayList<>(list);
            list.sort(
                    Comparator.comparing(
                            CommentDocument::getCreatedAt,
                            Comparator.nullsLast(Long::compareTo)).reversed());
        }

        String base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        List<Map<String, Object>> embedded = new ArrayList<>();
        for (CommentDocument d : list) {
            embedded.add(toHalComment(d, base));
        }

        String selfHref =
                ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/api/v2/comments/search/findByTripIdOrderByCreatedAtDesc")
                        .queryParam("tripId", tripId)
                        .build()
                        .toUriString();

        Map<String, Object> page = new LinkedHashMap<>();
        page.put("size", embedded.size());
        page.put("totalElements", embedded.size());
        page.put("totalPages", 1);
        page.put("number", 0);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("_embedded", Map.of("comments", embedded));
        body.put("_links", Map.of("self", Map.of("href", selfHref)));
        body.put("page", page);
        return body;
    }

    private static Map<String, Object> toHalComment(CommentDocument d, String baseUrl) {
        Map<String, Object> links = new LinkedHashMap<>();
        links.put("self", Map.of("href", baseUrl + "/api/v2/comments/" + d.getId()));
        links.put("trip", Map.of("href", baseUrl + "/api/v2/trips/" + d.getTripId()));
        links.put("user", Map.of("href", baseUrl + "/api/v2/users/" + d.getUserId()));

        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("content", d.getContent());
        entity.put(
                "createdAt",
                d.getCreatedAt() != null
                        ? Instant.ofEpochMilli(d.getCreatedAt()).toString()
                        : "");
        entity.put("_links", links);
        return entity;
    }

    // POST /api/v2/comments
    // Body: { "tripId": 1, "userId": 2, "content": "..." }
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        Long tripId = resolveTripId(body);
        Long userId = Long.parseLong(jwt.getSubject());
        String content = (String) body.get("content");
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content is required");
        }
        CommentDocument saved =
                commentRepository.save(new CommentDocument(tripId, userId, content)).block();
        String base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        return ResponseEntity.ok(toHalComment(saved, base));
    }

    // DELETE /api/v2/comments/{id}
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
        boolean isTripOwner =
                tripRepository
                        .findById(comment.getTripId())
                        .map(t -> t.getUser().getId().equals(callerId))
                        .orElse(false);
        if (!isAuthor && !isTripOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nicht berechtigt");
        }
        commentRepository.deleteById(id).block();
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
