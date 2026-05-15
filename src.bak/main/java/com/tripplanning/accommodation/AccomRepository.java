package com.tripplanning.accommodation;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(path = "accommodations", collectionResourceRel = "accommodations")
public interface AccomRepository extends JpaRepository<AccomEntity, Long> {
    Optional<AccomEntity> findByName(String name); // Exakte Suche

    Page<AccomEntity> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
