package com.tripplanning.external;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.tripplanning.external.ExternalInfoDtos.GeocodingResult;
import com.tripplanning.external.ExternalInfoDtos.TripExternalInfo;
import com.tripplanning.location.LocationEntity;

import reactor.core.publisher.Mono;

@Component
public class ExternalInfoClient {

    private final WebClient webClient;

    public ExternalInfoClient(
            WebClient.Builder builder,
            @Value("${tripplanning.services.external-info-base-url}") String baseUrl) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    public Mono<GeocodingResult> searchLocation(String query) {
        return webClient.get()
                .uri(uri -> uri.path("/api/v1/details/search/first").queryParam("q", query).build())
                .retrieve()
                .bodyToMono(GeocodingResult.class);
    }

    public Mono<TripExternalInfo> fetchExternalDetailsForLocation(LocationEntity location) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/details")
                        .queryParam("countryCode", location.getCountryCode())
                        .queryParam("location", location.getCity())
                        .queryParam("lat", location.getLatitude())
                        .queryParam("lon", location.getLongitude())
                        .build())
                .retrieve()
                .bodyToMono(TripExternalInfo.class);
    }
}
