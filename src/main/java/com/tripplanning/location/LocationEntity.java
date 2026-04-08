package com.tripplanning.location;

import com.tripplanning.trip.TripEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "location")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)

public class LocationEntity {

    public LocationEntity(
        TripEntity trip,
        String name,
        String description,
        String imageUrl
    ) {
        this.trip = trip;
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long location_id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private TripEntity trip;

    @Column(nullable = false)
    private String name;
    private String description;

    @Column(nullable = false, length = 500)
    private String imageUrl;

    public void setName (String name) {
        this.name = name;
    }

    public void setDescription (String description) {
        this.description = description;
    }

    public void setImageUrl (String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
