package com.tripplanning.location;

import org.springframework.stereotype.Service;

import com.tripplanning.external.ExternalInfoClient;
import com.tripplanning.external.ExternalInfoDtos.GeocodingResult;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepository;
    private final ExternalInfoClient externalInfoClient;

    public LocationEntity getOrCreateLocation(String cityName) {
        return getOrCreateLocation(cityName, null);
    }

    public LocationEntity getOrCreateLocation(String cityName, GeocodingResult resolvedFromClient) {
        String city = cityName == null ? "" : cityName.trim();
        if (city.isEmpty()) {
            throw new IllegalArgumentException("City name is required");
        }

        GeocodingResult geo = resolvedFromClient;
        if (geo == null) {
            try {
                geo = externalInfoClient.searchLocation(city).block();
            } catch (Exception ignored) {
                geo = null;
            }
        }

        if (geo != null) {
            final GeocodingResult resolved = geo;
            String storeCity = resolved.city() != null && !resolved.city().isBlank() ? resolved.city() : city;
            return locationRepository
                    .findByCityIgnoreCaseAndCountryCode(storeCity, resolved.countryCode())
                    .orElseGet(() -> {
                        LocationEntity newLoc = new LocationEntity();
                        newLoc.setCity(storeCity);
                        newLoc.setCountryCode(resolved.countryCode());
                        newLoc.setLatitude(resolved.lat());
                        newLoc.setLongitude(resolved.lon());
                        newLoc.setFormattedAddress(
                                resolved.displayName() != null ? resolved.displayName() : storeCity);
                        return locationRepository.save(newLoc);
                    });
        }

        return locationRepository
                .findByCityIgnoreCase(city)
                .orElseGet(() -> {
                    LocationEntity newLoc = new LocationEntity(city);
                    return locationRepository.save(newLoc);
                });
    }
}
