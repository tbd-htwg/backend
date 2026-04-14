package com.tripplanning.location;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
@RepositoryRestResource(path = "locations", collectionResourceRel = "locations")
public interface LocationRepository extends JpaRepository<LocationEntity, Long> {
    Optional<LocationEntity> findByName(String name); 
    // sucht, ob exakte Location vorhanden; falls nicht, Neuanlage

}
