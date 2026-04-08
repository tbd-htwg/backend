package com.tripplanning.accommodation;

import java.util.ArrayList;
import java.util.List;

import com.tripplanning.trip.TripEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "accommodation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)

public class AccomEntity {

    public AccomEntity(String name, String type, String address) {
        this.name = name;
        this.type = type;
        this.address = address;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long accom_id;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false)
    private String name;
    private String address;

    public void setName (String name) {
        this.name = name;
    }

    public void setType (String type) {
        this.type = type;
    }

    public void setAddress (String address) {
        this.address = address;
    }

    @ManyToMany(mappedBy = "accommodation")
    private List<TripEntity> trips = new ArrayList<>();

}
