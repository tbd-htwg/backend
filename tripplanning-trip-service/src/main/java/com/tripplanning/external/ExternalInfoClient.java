package com.tripplanning.external;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.tripplanning.external.ExternalInfoDtos.PlaceDetailsResult;

import reactor.core.publisher.Mono;

@Component
public class ExternalInfoClient {

    private final WebClient webClient;
    private final String internalSecret;

    public ExternalInfoClient(
            WebClient.Builder builder,
            @Value("${tripplanning.services.external-info-base-url}") String baseUrl,
            @Value("${tripplanning.services.internal-secret:}") String internalSecret) {
        this.webClient = builder.baseUrl(baseUrl).build();
        this.internalSecret = internalSecret;
    }

    public Mono<PlaceDetailsResult> fetchPlaceDetails(String placeId) {
        return fetchPlaceDetails(placeId, false);
    }

    /** @param fresh when true, bypasses Redis place cache (write path). */
    public Mono<PlaceDetailsResult> fetchPlaceDetails(String placeId, boolean fresh) {
        return webClient
                .get()
                .uri(
                        uriBuilder ->
                                uriBuilder
                                        .path("/internal/location-pack")
                                        .queryParam("placeId", placeId)
                                        .queryParam("fresh", fresh)
                                        .build())
                .headers(
                        headers -> {
                            if (internalSecret != null && !internalSecret.isBlank()) {
                                headers.set("X-Internal-Secret", internalSecret);
                            }
                        })
                .retrieve()
                .bodyToMono(PlaceDetailsResult.class)
                .onErrorMap(ExternalInfoClient::mapError);
    }

    private static Throwable mapError(Throwable error) {
        if (error instanceof WebClientResponseException webError) {
            if (webError.getStatusCode() == HttpStatus.NOT_FOUND) {
                return error;
            }
            if (webError.getStatusCode().is4xxClientError()) {
                return new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "External info service rejected place lookup: " + webError.getStatusCode());
            }
        }
        return error;
    }
}
