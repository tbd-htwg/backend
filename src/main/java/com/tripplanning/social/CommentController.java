package com.tripplanning.social;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v2/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    // GET /api/v2/comments/search/findByTripIdOrderByCreatedAtDesc?tripId=1
    @GetMapping("/search/findByTripIdOrderByCreatedAtDesc")
    public Page<CommentDocument> getByTrip(
            @RequestParam Long tripId,
            Pageable pageable) {
        return commentService.getCommentsByTrip(tripId, pageable);
    }

    // POST /api/v2/comments
    // Body: { "tripId": 1, "userId": 2, "content": "..." }
    @PostMapping
    public ResponseEntity<CommentDocument> create(@RequestBody Map<String, Object> body) {
        Long tripId = parseLong(body, "tripId");
        Long userId = parseLong(body, "userId");
        String content = (String) body.get("content");
        CommentDocument saved = commentService.createComment(tripId, userId, content);
        return ResponseEntity.ok(saved);
    }

    // DELETE /api/v2/comments/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        commentService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private Long parseLong(Map<String, Object> body, String key) {
        Object val = body.get(key);
        if (val instanceof Number n) return n.longValue();
        // Unterstütze auch URI-Format: "/api/v2/trips/1"
        if (val instanceof String s) {
            String[] parts = s.split("/");
            return Long.parseLong(parts[parts.length - 1]);
        }
        throw new IllegalArgumentException("Missing field: " + key);
    }
}
