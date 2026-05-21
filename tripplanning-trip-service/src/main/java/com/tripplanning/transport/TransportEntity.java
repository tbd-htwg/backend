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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;

@Entity
@Table(name = "transport")
@Indexed
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TransportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column
    private String startGooglePlaceId;

    @Column
    private String endGooglePlaceId;

    @FullTextField(analyzer = "english")
    @Column(length = 500)
    private String startAddress;

    @FullTextField(analyzer = "english")
    @Column(length = 500)
    private String endAddress;

    @Builder.Default
    @ManyToMany(mappedBy = "transports")
    private List<TripEntity> trips = new ArrayList<>();
}
