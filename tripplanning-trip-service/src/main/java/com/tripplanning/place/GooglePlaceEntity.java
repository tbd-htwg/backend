package com.tripplanning.place;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "google_places")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GooglePlaceEntity {

    @Id
    @Column(name = "google_place_id", nullable = false)
    private String googlePlaceId;

    @Column(nullable = false)
    private String placeName;

    @Column(nullable = false)
    private String cityName;

    @Column(columnDefinition = "text")
    private String formattedAddress;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(nullable = false, length = 10)
    private String countryCode;

    @Column(nullable = false)
    private Instant updatedAt;
}
