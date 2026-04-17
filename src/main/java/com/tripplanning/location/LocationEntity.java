package com.tripplanning.location;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import org.hibernate.search.mapper.pojo.mapping.definition.annotation.KeywordField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;



@Entity
@Table(name = "location")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)

public class LocationEntity {

    public LocationEntity(String name) {
        this.name = name;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false, unique = true) // LocationEnitity als Liste aus eindeutigen Orten
    @FullTextField(analyzer = "english")
    @KeywordField(name = "destination_keyword")
    private String name;

    }

