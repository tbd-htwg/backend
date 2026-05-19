package com.tripplanning.external;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.tripplanning.external.ExternalInfoDtos.TripExternalInfo;
import com.tripplanning.external.ExternalInfoDtos.PlaceDetailsResult;

import reactor.core.publisher.Mono;

@Component
public class ExternalInfoClient {

    private final WebClient webClient;

    public ExternalInfoClient(
            WebClient.Builder builder,
            @Value("${tripplanning.services.external-info-base-url}") String baseUrl) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    
    public Mono<PlaceDetailsResult> fetchPlaceDetails(String placeId) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v2/external/details") 
                        .queryParam("placeId", placeId)
                        .build())
                .retrieve()
                .bodyToMono(Map.class) 
                .map(response -> {
                    if (response != null && response.containsKey("locationInfo")) {
                        Map<String, Object> geoMap = (Map<String, Object>) response.get("locationInfo");
                        return new PlaceDetailsResult(
                            (String) geoMap.get("placeName"),
                            (String) geoMap.get("cityName"),
                            (String) geoMap.get("formattedAddress"),
                            (double) geoMap.get("lat"),
                            (double) geoMap.get("lon"),
                            (String) geoMap.get("countryCode")
                        );
                    }
                    return null;
                });
    }


    public Mono<TripExternalInfo> fetchExternalDetailsForLocation(String googlePlaceId) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v2/external/location-pack")
                        .queryParam("placeId", googlePlaceId) 
                        .build())
                .retrieve()
                .bodyToMono(TripExternalInfo.class);
    }
}