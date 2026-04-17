package com.tripplanning.search;

import java.util.List;
import java.util.stream.Collectors;

import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import com.tripplanning.trip.TripEntity;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TripSearchService {

    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<TripSearchDto> search(String terms) {
        SearchSession searchSession = Search.session(entityManager);

        List<TripEntity> hits = searchSession.search(TripEntity.class)
            .where(f -> f.match()
                .fields("title", "shortDescription", 
                        "user.username", 
                        "tripLocations.location.name", 
                        "accommodations.name", 
                        "transports.type")
                .matching(terms)
                .fuzzy(2))
            .fetchHits(50);

        // Umwandlung von Entity zu DTO
        return hits.stream()
            .map(trip -> TripSearchDto.builder()
                .id(trip.getId())
                .title(trip.getTitle())
                .author(trip.getUser() != null ? trip.getUser().getName() : "Unbekannt")
                .shortDescription(trip.getShortDescription())
                .locations(trip.getTripLocations().stream()
                    .map(tl -> tl.getLocation().getName())
                    .collect(Collectors.toList()))
                .build())
            .collect(Collectors.toList());
    }
}