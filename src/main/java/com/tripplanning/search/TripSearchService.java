package com.tripplanning.search;

import java.util.List;
import java.util.stream.Collectors;

import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripplanning.trip.TripEntity;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TripSearchService {

    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public Page<TripSearchDto> search(String terms, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        if (terms == null || terms.trim().isEmpty()) {
            return Page.empty(pageable);
        }

        String trimmed = terms.trim();
        SearchSession searchSession = Search.session(entityManager);

        int offset = Math.toIntExact(pageable.getOffset());
        SearchResult<TripEntity> result = searchSession.search(TripEntity.class)
            .where(f -> f.match()
                .fields("title", "shortDescription", "destination",
                        "user.name",
                        "tripLocations.location.name",
                        "accommodations.name",
                        "transports.type")
                .matching(trimmed)
                .fuzzy(1))
            .fetch(offset, size);

        List<TripSearchDto> content = result.hits().stream()
            .map(this::toDto)
            .collect(Collectors.toList());

        long totalHits = result.total().hitCount();
        return new PageImpl<>(content, pageable, totalHits);
    }

    private TripSearchDto toDto(TripEntity trip) {
        return TripSearchDto.builder()
            .id(trip.getId())
            .title(trip.getTitle())
            .author(trip.getUser() != null ? trip.getUser().getName() : "Unbekannt")
            .shortDescription(trip.getShortDescription())
            .locations(trip.getTripLocations().stream()
                .map(tl -> tl.getLocation().getName())
                .collect(Collectors.toList()))
            .build();
    }
}