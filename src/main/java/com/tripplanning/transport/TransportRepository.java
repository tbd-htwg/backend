package com.tripplanning.transport;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransportRepository extends JpaRepository<TransportEntity, Long> {
    Optional<TransportEntity> findByType(String type);
    // sucht, ob Transporttyp vorhanden; falls nicht, Neuanlage
}
