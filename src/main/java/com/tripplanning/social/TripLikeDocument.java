package com.tripplanning.social;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Document(collectionName = "likes")
@Getter
@NoArgsConstructor
public class TripLikeDocument {

    /** Stable Firestore document id: one document per (userId, tripId) pair. */
    public static String documentId(Long userId, Long tripId) {
        return userId + "_" + tripId;
    }

    public TripLikeDocument(Long userId, Long tripId) {
        this.id = documentId(userId, tripId);
        this.userId = userId;
        this.tripId = tripId;
    }

    @DocumentId
    private String id;

    private Long userId;
    private Long tripId;
}
