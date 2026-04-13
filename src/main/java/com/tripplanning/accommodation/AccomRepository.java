package com.tripplanning.accommodation;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource
public interface AccomRepository extends JpaRepository<AccomEntity, Long> {
    Optional<AccomEntity> findByName(String name); // Exakte Suche
    
    List<AccomEntity> findByNameContaining(String name); // Mehrere Ergebnisse
}
