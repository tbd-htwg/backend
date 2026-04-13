package com.tripplanning.comment;

import java.time.LocalDateTime;

import com.tripplanning.trip.TripEntity;
import com.tripplanning.user.UserEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "comment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)

public class CommentEntity {

    public CommentEntity (UserEntity user, TripEntity trip, String content) {
        this.user = user;
        this.trip = trip;
        this.content = content;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToOne(optional = false)
    @JoinColumn(name = "userId")
    private UserEntity user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "tripId")
    private TripEntity trip;

    //zum nachträglichen Bearbeiten von Kommentaren
    public void setContent (String content) {
        this.content = content;
    }

}
