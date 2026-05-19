package com.tripplanning.externalinfo.ApiProxyServices;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.tripplanning.externalinfo.dto.ExternalInfoDto.GeocodingResult;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class GeocodingApi {

    private static final int DEFAULT_SEARCH_LIMIT = 8;

    private final WebClient webClient;

    public GeocodingApi(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public Mono<GeocodingResult> searchLocation(String query) {
        return searchLocations(query, 1).flatMap(list -> list.isEmpty() ? Mono.empty() : Mono.just(list.get(0)));
    }

    public Mono<List<GeocodingResult>> searchLocations(String query) {
        return searchLocations(query, DEFAULT_SEARCH_LIMIT);
    }

    public Mono<List<GeocodingResult>> searchLocations(String query, int limit) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            return Mono.just(List.of());
        }
        int effectiveLimit = Math.min(Math.max(limit, 1), 10);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("nominatim.openstreetmap.org")
                        .path("/search")
                        .queryParam("q", trimmed)
                        .queryParam("format", "json")
                        .queryParam("addressdetails", 1)
                        .queryParam("limit", effectiveLimit)
                        .build())
                .header("User-Agent", "TripPlannerProject/1.0")
                .retrieve()
                .bodyToMono(List.class)
                .map(list -> {
                    List<GeocodingResult> results = new ArrayList<>();
                    for (Object item : list) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> row = (Map<String, Object>) item;
                        mapResult(row).ifPresent(results::add);
                    }
                    return results;
                })
                .onErrorResume(e -> {
                    log.error("Geocoding error: {}", e.getMessage());
                    return Mono.just(List.of());
                });
    }

    private static java.util.Optional<GeocodingResult> mapResult(Map<String, Object> firstResult) {
        @SuppressWarnings("unchecked")
        Map<String, Object> address = (Map<String, Object>) firstResult.get("address");

        String city = "Unknown";
        if (address != null) {
            if (address.containsKey("city")) {
                city = (String) address.get("city");
            } else if (address.containsKey("town")) {
                city = (String) address.get("town");
            } else if (address.containsKey("village")) {
                city = (String) address.get("village");
            } else if (address.containsKey("municipality")) {
                city = (String) address.get("municipality");
            }
        }

        Object displayNameObj = firstResult.get("display_name");
        if (displayNameObj == null) {
            return java.util.Optional.empty();
        }
        String displayName = displayNameObj.toString();
        double lat = Double.parseDouble(firstResult.get("lat").toString());
        double lon = Double.parseDouble(firstResult.get("lon").toString());
        String countryCode = address != null ? (String) address.get("country_code") : "DE";

        return java.util.Optional.of(new GeocodingResult(city, displayName, lat, lon, countryCode.toUpperCase()));
    }
}
