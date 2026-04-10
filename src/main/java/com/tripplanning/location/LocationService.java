package com.tripplanning.location;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepository;

    @Transactional
    public LocationEntity getOrCreateLocation(String name) {
        return locationRepository.findByName(name)
            .orElseGet(() -> locationRepository.save(new LocationEntity(name)));
    }
}
