package com.tripplanning.social;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "comments")
@CompoundIndex(name = "trip_createdAt", def = "{'tripId': 1, 'createdAt': -1}")
@Getter
@NoArgsConstructor
public class CommentDocument {

    public CommentDocument(Long tripId, Long userId, String content) {
        this.tripId = tripId;
        this.userId = userId;
        this.content = content;
        this.createdAt = Instant.now();
    }

    @Id
    private String id;

    private Long tripId;
    private Long userId;
    private String content;
    private Instant createdAt;

    public void setContent(String content) {
        this.content = content;
    }
}
