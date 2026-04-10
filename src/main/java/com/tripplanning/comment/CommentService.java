package com.tripplanning.comment;

import com.tripplanning.api.dto.response.CommentResponse;
import com.tripplanning.trip.TripEntity;
import com.tripplanning.user.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;

    @Transactional
    public CommentResponse addComment(UserEntity author, TripEntity trip, String content) {
        CommentEntity comment = new CommentEntity(author, trip, content);
        CommentEntity saved = commentRepository.save(comment);
        
        return new CommentResponse(
            saved.getComment_id(), 
            author.getName(), 
            saved.getComment(), 
            saved.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsByTrip(long tripId) {
        return commentRepository.findByTripIdOrderByCreatedAtDesc(tripId).stream()
            .map(c -> new CommentResponse(
                c.getComment_id(), 
                c.getUser().getName(), 
                c.getComment(), 
                c.getCreatedAt()
            ))
            .toList();
    }
}