package com.tripplanning.accommodation;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;

@RepositoryRestResource(path = "accommodations", collectionResourceRel = "accommodations")
public interface AccomRepository extends JpaRepository<AccomEntity, Long> {
    Optional<AccomEntity> findByName(String name); // Exakte Suche

    Page<AccomEntity> findByNameContainingIgnoreCase(String name, Pageable pageable);

    @Override
    @RestResource(exported = false)
    <S extends AccomEntity> S save(S entity);
}
