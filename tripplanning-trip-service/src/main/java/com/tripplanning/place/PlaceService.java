package com.tripplanning.place;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.tripplanning.external.ExternalInfoClient;
import com.tripplanning.external.ExternalInfoDtos.PlaceDetailsResult;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlaceService {

    private final GooglePlaceRepository googlePlaceRepository;
    private final ExternalInfoClient externalInfoClient;

    /**
     * Live Google lookup (bypasses Redis place cache), upserts {@code google_places}, returns persisted row.
     */
    @Transactional
    public GooglePlaceEntity resolvePlaceForWrite(String placeId) {
        String normalizedId = normalizePlaceId(placeId);
        if (normalizedId.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "googlePlaceId is required.");
        }
        PlaceDetailsResult details = requireLiveDetails(normalizedId);
        return upsert(normalizedId, details);
    }

    public java.util.Optional<GooglePlaceEntity> findPlaceForRead(String placeId) {
        String normalizedId = normalizePlaceId(placeId);
        if (normalizedId.isEmpty()) {
            return java.util.Optional.empty();
        }
        return googlePlaceRepository.findById(normalizedId);
    }

    public PlaceDetailsResult toDetailsResult(GooglePlaceEntity entity) {
        return new PlaceDetailsResult(
                entity.getPlaceName(),
                entity.getCityName(),
                entity.getFormattedAddress() != null ? entity.getFormattedAddress() : "",
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getCountryCode());
    }

    private PlaceDetailsResult requireLiveDetails(String normalizedId) {
        try {
            PlaceDetailsResult geo = externalInfoClient.fetchPlaceDetails(normalizedId, true).block();
            if (geo == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Place could not be found in Google.");
            }
            return geo;
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException.NotFound e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Place could not be found in Google.");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Could not resolve place details from external-info service.",
                    e);
        }
    }

    private GooglePlaceEntity upsert(String normalizedId, PlaceDetailsResult details) {
        GooglePlaceEntity entity =
                googlePlaceRepository
                        .findById(normalizedId)
                        .orElseGet(
                                () ->
                                        GooglePlaceEntity.builder()
                                                .googlePlaceId(normalizedId)
                                                .build());
        entity.setPlaceName(details.placeName());
        entity.setCityName(details.cityName());
        entity.setFormattedAddress(details.formattedAddress());
        entity.setLatitude(details.lat());
        entity.setLongitude(details.lon());
        entity.setCountryCode(details.countryCode());
        entity.setUpdatedAt(Instant.now());
        return googlePlaceRepository.save(entity);
    }

    static String normalizePlaceId(String placeId) {
        if (placeId == null) {
            return "";
        }
        String trimmed = placeId.trim();
        if (trimmed.startsWith("places/")) {
            return trimmed.substring("places/".length());
        }
        return trimmed;
    }
}
