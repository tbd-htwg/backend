package com.tripplanning.location;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(path = "locations", collectionResourceRel = "locations")
public interface LocationRepository extends JpaRepository<LocationEntity, Long> {

    Page<LocationEntity> findByCityContainingIgnoreCase(String city, Pageable pageable);

    Optional<LocationEntity> findByCityIgnoreCaseAndCountryCode(String city, String countryCode);

    Optional<LocationEntity> findByCityIgnoreCase(String city);
}
