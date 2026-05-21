package com.tripplanning.transport;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;

@RepositoryRestResource(path = "transports", collectionResourceRel = "transports")
public interface TransportRepository extends JpaRepository<TransportEntity, Long> {

    @Override
    @RestResource(exported = false)
    <S extends TransportEntity> S save(S entity);
}
