package com.tripplanning.location;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "location")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)

public class LocationEntity {

    public LocationEntity(String name) {
        this.name = name;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long location_id;

    @Column(nullable = false, unique = true) // LocationEnitity als Liste aus eindeutigen Orten
    private String name;

    public void setName (String name) {
        this.name = name;
    }

}
