package com.tripplanning.tripLocation;

import java.time.LocalDateTime;

import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexedEmbedded;

import com.tripplanning.location.LocationEntity;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tripLocations")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)

public class TripLocationEntity {

    @Id // ermöglicht, dass eine Location auch mehrmals während eines Trips besucht werden kann
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "tripId")
    private TripEntity trip;

    @IndexedEmbedded
    @ManyToOne(optional = false)
    @JoinColumn(name = "locationId")
    private LocationEntity location;

    @Column(nullable = true, length = 500)
    private String imageUrl; // Spezifische User Fotos optional

    @Column(nullable = true, columnDefinition = "TEXT")
    private String description; // Persönliche User Beschreibung optional

    @Column(nullable = false)
    private LocalDateTime startDate; 
    
    @Column(nullable = false)
    private LocalDateTime endDate;

    public TripLocationEntity(TripEntity trip, LocationEntity location, String imageUrl, String description, LocalDateTime startDate, LocalDateTime endDate) {
        this.trip = trip;
        this.location = location;
        this.imageUrl = imageUrl;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
    }
}

