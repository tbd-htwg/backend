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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;


@Entity
@Table(name = "accommodation")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor

public class AccomEntity {

    public AccomEntity(String name, String type, String address) {
        this.name = name;
        this.type = type;
        this.address = address;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false)
    @FullTextField(analyzer = "english")
    private String name;
    private String address;

    @Builder.Default
    @ManyToMany(mappedBy = "accommodations")
    private List<TripEntity> trips = new ArrayList<>();

}
