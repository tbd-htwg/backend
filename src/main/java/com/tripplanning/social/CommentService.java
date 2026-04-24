package com.tripplanning.social;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentMongoRepository commentRepository;

    public Page<CommentDocument> getCommentsByTrip(Long tripId, Pageable pageable) {
        return commentRepository.findByTripIdOrderByCreatedAtDesc(tripId, pageable);
    }

    public CommentDocument createComment(Long tripId, Long userId, String content) {
        return commentRepository.save(new CommentDocument(tripId, userId, content));
    }

    public void deleteByUserId(Long userId) {
        commentRepository.deleteByUserId(userId);
    }

    public void deleteById(String id) {
        commentRepository.deleteById(id);
    }
}
