package com.tripplanning.transport;

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
@Table(name = "transport")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)

public class TransportEntity {

    public TransportEntity(String type) {
        this.type = type;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long transport_id;

    @Column(nullable = false, length = 50)
    private String type;

    public void setType (String type) {
        this.type = type;
    }

    @ManyToMany(mappedBy = "transport")
    private List<TripEntity> trips = new ArrayList<>();







}
