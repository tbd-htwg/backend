package com.tripplanning.tripLocation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;

import com.tripplanning.trip.TripEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "trip_locations")
@Indexed
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)

public class TripLocationEntity {

    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String googlePlaceId;

    @Column(nullable = false)
    @FullTextField(analyzer = "english")
    private String placeName;

    @Column(nullable = false)
    @FullTextField(analyzer = "english")
    private String cityName;

    @ManyToOne(optional = false)
    @JoinColumn(name = "tripId")
    private TripEntity trip;

    @Builder.Default
    @OneToMany(mappedBy = "tripLocation", orphanRemoval = true, cascade = CascadeType.ALL)
    private List<TripLocationImageEntity> images = new ArrayList<>();


    @Column(nullable = true, columnDefinition = "TEXT")
    private String description; // Persönliche User Beschreibung optional

    @Column(nullable = false)
    private LocalDateTime startDate; 
    
    @Column(nullable = false)
    private LocalDateTime endDate;

    public TripLocationEntity(TripEntity trip, String googlePlaceId, String description, LocalDateTime startDate, LocalDateTime endDate) {
        this.trip = trip;
        this.googlePlaceId = googlePlaceId;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
    }
}

