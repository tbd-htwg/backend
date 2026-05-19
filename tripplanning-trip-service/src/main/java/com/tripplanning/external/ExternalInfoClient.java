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

     //Holt AUSSCHLIESSLICH die Google-Ortsdetails vom external-info-service
    public Mono<PlaceDetailsResult> fetchPlaceDetails(String placeId) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v2/external/location-pack")
                        .queryParam("placeId", placeId)
                        .build())
                .retrieve()
                .bodyToMono(PlaceDetailsResult.class);
    }
}