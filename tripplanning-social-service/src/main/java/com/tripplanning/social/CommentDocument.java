package com.tripplanning.social;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Document(collectionName = "comments")
@Getter
@NoArgsConstructor
public class CommentDocument {

    public CommentDocument(Long tripId, Long userId, String content) {
        this.tripId = tripId;
        this.userId = userId;
        this.content = content;
        this.createdAt = Instant.now().toEpochMilli();
    }

    @DocumentId
    private String id;

    private Long tripId;
    private Long userId;
    private String content;
    private Long createdAt; // epoch millis, Firestore kennt kein Instant direkt

    public void setContent(String content) {
        this.content = content;
    }
}
