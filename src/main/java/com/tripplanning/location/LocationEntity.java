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
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import org.hibernate.search.mapper.pojo.mapping.definition.annotation.KeywordField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;



@Entity
@Table(name = "location")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Indexed

public class LocationEntity {

    public LocationEntity(String name) {
        this.name = name;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false, unique = true) // LocationEnitity als Liste aus eindeutigen Orten
    @FullTextField(analyzer = "english")
    @KeywordField(name = "destination_keyword", normalizer = "lowercase")
    private String name;

    /** Inverse of {@link TripLocationEntity#location}; required for Hibernate Search reindexing. */
    @OneToMany(mappedBy = "location")
    private List<TripLocationEntity> tripLocations = new ArrayList<>();

    }

