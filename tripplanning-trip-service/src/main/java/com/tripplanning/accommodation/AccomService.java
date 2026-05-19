package com.tripplanning.accommodation;

import org.springframework.stereotype.Service;

import com.tripplanning.external.ExternalInfoClient;
import com.tripplanning.external.ExternalInfoDtos.PlaceDetailsResult;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccomService {

    private final AccomRepository accomRepository;
    private final ExternalInfoClient externalInfoClient;

    public AccomEntity createAccommodation(AccomEntity accomFromFrontend) {
        // Da das Repository blockiert (JPA), blockieren wir den WebClient-Aufruf hier einmalig (.block())
        PlaceDetailsResult geo = externalInfoClient.fetchPlaceDetails(accomFromFrontend.getGooglePlaceId())
                .block(); // Wartet synchron, bis Google antwortet
        
        if (geo == null) {
            throw new RuntimeException("Accommodation could not be found in Google.");
        }

        // Daten von Google auf die Entity übertragen
        accomFromFrontend.setName(geo.placeName());
        accomFromFrontend.setCityName(geo.cityName());
        accomFromFrontend.setAddress(geo.formattedAddress());

        // Standard JPA-Speicherung
        return accomRepository.save(accomFromFrontend);
    }
}