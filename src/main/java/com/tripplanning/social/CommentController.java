package com.tripplanning.social;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentRepository commentRepository;

    // GET /api/v2/comments/search/findByTripIdOrderByCreatedAtDesc?tripId=1
    @GetMapping("/search/findByTripIdOrderByCreatedAtDesc")
    public List<CommentDocument> getByTrip(@RequestParam Long tripId) {
        return commentRepository.findByTripIdOrderByCreatedAtDesc(tripId)
                .collectList()
                .block();
    }

    // POST /api/v2/comments
    // Body: { "tripId": 1, "userId": 2, "content": "..." }
    @PostMapping
    public ResponseEntity<CommentDocument> create(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        Long tripId = parseLong(body, "tripId");
        Long userId = Long.parseLong(jwt.getSubject());
        String content = (String) body.get("content");
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content is required");
        }
        CommentDocument saved = commentRepository.save(new CommentDocument(tripId, userId, content))
                .block();
        return ResponseEntity.ok(saved);
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
        if (!comment.getUserId().equals(callerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nicht berechtigt");
        }
        commentRepository.deleteById(id).block();
        return ResponseEntity.noContent().build();
    }

    private Long parseLong(Map<String, Object> body, String key) {
        Object val = body.get(key);
        if (val instanceof Number n) return n.longValue();
        if (val instanceof String s) {
            String[] parts = s.split("/");
            return Long.parseLong(parts[parts.length - 1]);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing field: " + key);
    }
}
