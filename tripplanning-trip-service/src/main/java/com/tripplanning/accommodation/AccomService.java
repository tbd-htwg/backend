package com.tripplanning.accommodation;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.tripplanning.external.ExternalInfoDtos.PlaceDetailsResult;
import com.tripplanning.external.PlaceEnrichmentHelper;
import com.tripplanning.trip.read.TripCacheEvictor;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccomService {

    private final AccomRepository accomRepository;
    private final PlaceEnrichmentHelper placeEnrichmentHelper;
    private final TripCacheEvictor tripCacheEvictor;

    public AccomCreatedResponse createAccommodation(AccomRequest.CreateAccommodationRequest request) {
        if (request.checkOutDate().isBefore(request.checkInDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Check-out date must be on or after check-in date.");
        }
        if (request.cost().signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cost must be non-negative.");
        }

        PlaceDetailsResult geo = placeEnrichmentHelper.requirePlaceDetails(request.googlePlaceId());

        AccomEntity entity =
                AccomEntity.builder()
                        .type("")
                        .googlePlaceId(request.googlePlaceId())
                        .name(geo.placeName())
                        .cityName(geo.cityName())
                        .address(geo.formattedAddress())
                        .checkInDate(request.checkInDate())
                        .checkOutDate(request.checkOutDate())
                        .cost(request.cost())
                        .currency(request.currency())
                        .build();

        return AccomCreatedResponse.from(accomRepository.save(entity));
    }

    public AccomCreatedResponse updateAccommodation(long id, AccomRequest.UpdateAccommodationRequest request) {
        AccomEntity entity =
                accomRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Accommodation not found."));

        if (request.checkOutDate().isBefore(request.checkInDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Check-out date must be on or after check-in date.");
        }
        if (request.cost().signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cost must be non-negative.");
        }

        PlaceDetailsResult geo = placeEnrichmentHelper.requirePlaceDetails(request.googlePlaceId());

        entity.setGooglePlaceId(request.googlePlaceId());
        entity.setName(geo.placeName());
        entity.setCityName(geo.cityName());
        entity.setAddress(geo.formattedAddress());
        entity.setCheckInDate(request.checkInDate());
        entity.setCheckOutDate(request.checkOutDate());
        entity.setCost(request.cost());
        entity.setCurrency(request.currency());

        AccomCreatedResponse response = AccomCreatedResponse.from(accomRepository.save(entity));
        tripCacheEvictor.evictAllFeeds();
        return response;
    }
}
