package com.tripplanning.transport;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(path = "transports", collectionResourceRel = "transports")
public interface TransportRepository extends JpaRepository<TransportEntity, Long> {
    Optional<TransportEntity> findByType(String type);
    // sucht, ob Transporttyp vorhanden; falls nicht, Neuanlage

    List<TransportEntity> findByTypeContainingIgnoreCase(String type);
}
