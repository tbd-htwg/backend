package com.tripplanning.tripLocation;

import com.tripplanning.location.LocationEntity;
import com.tripplanning.trip.TripEntity;

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
@Table(name = "trip_locations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)

public class TripLocationEntity {

    @Id // ermöglicht, dass eine Location auch mehrmals während eines Trips besucht werden kann
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "trip_id")
    private TripEntity trip;

    @ManyToOne(optional = false)
    @JoinColumn(name = "location_id")
    private LocationEntity location;

    @Column(length = 500)
    private String imageUrl; // Spezifische User Fotos

    @Lob
    private String description; // Persönliche User Beschreibung

    public TripLocationEntity(TripEntity trip, LocationEntity location, String imageUrl, String description) {
        this.trip = trip;
        this.location = location;
        this.imageUrl = imageUrl;
        this.description = description;
    }
}

