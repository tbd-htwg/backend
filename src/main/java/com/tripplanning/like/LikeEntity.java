package com.tripplanning.like;

import java.time.LocalDateTime;

import com.tripplanning.trip.TripEntity;
import com.tripplanning.user.UserEntity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "likes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)

public class LikeEntity {

    public LikeEntity(UserEntity user, TripEntity trip) {
        this.user = user;
        this.trip = trip;
        this.like_id = new LikeId(user.getUser_id(), trip.getTrip_id());
    }

    @EmbeddedId
    private LikeId like_id;

    @ManyToOne
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @ManyToOne
    @MapsId("tripId")
    @JoinColumn(name = "trip_id")
    private TripEntity trip;

    private LocalDateTime likedAt = LocalDateTime.now();

}
