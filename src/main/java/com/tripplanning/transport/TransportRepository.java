package com.tripplanning.transport;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.Optional;

@RepositoryRestResource(path = "transports", collectionResourceRel = "transports")
public interface TransportRepository extends JpaRepository<TransportEntity, Long> {
    Optional<TransportEntity> findByType(String type);
    // sucht, ob Transporttyp vorhanden; falls nicht, Neuanlage
}
