package com.tripplanning.social;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "likes")
@CompoundIndex(name = "userId_tripId_unique", def = "{'userId': 1, 'tripId': 1}", unique = true)
@Getter
@NoArgsConstructor
public class TripLikeDocument {

    public TripLikeDocument(Long userId, Long tripId) {
        this.userId = userId;
        this.tripId = tripId;
    }

    @Id
    private String id;

    private Long userId;
    private Long tripId;
}
