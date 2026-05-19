package com.tripplanning.location;

import java.util.ArrayList;
import java.util.List;

import com.tripplanning.tripLocation.TripLocationEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.KeywordField;

@Entity
@Table(name = "location")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class LocationEntity {

    public LocationEntity(String city) {
        this.city = city;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false)
    @FullTextField(analyzer = "english")
    @KeywordField(name = "destination_keyword")
    private String city;

    private String countryCode;

    private double latitude;

    private double longitude;

    private String formattedAddress;

    @OneToMany(mappedBy = "location")
    private List<TripLocationEntity> tripLocations = new ArrayList<>();
}
