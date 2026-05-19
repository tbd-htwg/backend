package com.tripplanning.externalinfo.ApiProxyServices;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import com.tripplanning.externalinfo.dto.ExternalInfoDtos.PlaceDetailsResult;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;


@Service
@Slf4j
public class GooglePlacesApi {
    private final WebClient webClient;

    @Value("${external-api.google.maps.api-key}")
    private String apiKey;

    @Value("${external-api.google.maps.base-url}")
    private String baseUrl;

    public GooglePlacesApi(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    //Holt die vollen Details zu einer konkreten Place-ID (Inkl. Stadt und Land)
    @SuppressWarnings("unchecked")
    public Mono<PlaceDetailsResult> getPlaceDetails(String placeId) {
        return webClient.get()
            .uri(uriBuilder -> UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/maps/api/place/details/json")
                .queryParam("place_id", placeId)
                .queryParam("fields", "name,formatted_address,geometry,address_components")
                .queryParam("key", apiKey)
                .build()
                .toUri())
            .retrieve()
            .bodyToMono(Map.class)
            .map(response -> {
                Map<String, Object> result = (Map<String, Object>) response.get("result");
                if (result == null || result.isEmpty()) return null;

                String placeName = (String) result.get("name"); 
                String formattedAddress = (String) result.get("formatted_address");

                Map<String, Object> geometry = (Map<String, Object>) result.get("geometry");
                if (geometry == null) return null;
                
                Map<String, Object> location = (Map<String, Object>) geometry.get("location");
                if (location == null) return null;
                
                double lat = ((Number) location.get("lat")).doubleValue();
                double lon = ((Number) location.get("lng")).doubleValue();

                List<Map<String, Object>> components = (List<Map<String, Object>>) result.get("address_components");
                String cityName = extractComponent(components, "locality");
                String countryCode = extractComponent(components, "country");

                if ("Unknown".equals(cityName) || cityName.isEmpty()) {
                    cityName = extractComponent(components, "postal_town");
                }
                // Falls es immer noch unbekannt ist, nehmen wir den placeName als Fallback für den Stopp
                if ("Unknown".equals(cityName)) {
                    cityName = placeName;
                }

                return new PlaceDetailsResult(placeName, cityName, formattedAddress, lat, lon, countryCode.toUpperCase());
            })
            .onErrorResume(e -> {
                log.error("Google Places Details API Error: {}", e.getMessage());
                return Mono.empty();
            });
    }

    //Reine Textsuche für das Frontend-Suchfeld (Liefert kompakte Trefferliste)
    @SuppressWarnings("unchecked")
    public Mono<List<PlaceDetailsResult>> searchLocations(String query) {
        return webClient.get()
            .uri(uriBuilder -> UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/maps/api/place/textsearch/json") 
                .queryParam("query", query)
                .queryParam("key", apiKey)
                .build()
                .toUri())
            .retrieve()
            .bodyToMono(Map.class)
            .map(response -> {
                List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
                List<PlaceDetailsResult> searchResults = new ArrayList<>();
                
                if (results != null) {
                    for (Map<String, Object> place : results) {
                        String placeName = (String) place.get("name");
                        String formattedAddress = (String) place.get("formatted_address");
                        String placeId = (String) place.get("place_id"); 

                        Map<String, Object> geometry = (Map<String, Object>) place.get("geometry");
                        if (geometry == null) continue;
                        
                        Map<String, Object> location = (Map<String, Object>) geometry.get("location");
                        if (location == null) continue;
                        
                        double lat = ((Number) location.get("lat")).doubleValue();
                        double lon = ((Number) location.get("lng")).doubleValue();
                        
                        searchResults.add(new PlaceDetailsResult(placeName, placeId, formattedAddress, lat, lon, "XX"));
                    }
                }
                return searchResults;
            })
            .onErrorResume(e -> {
                log.error("Google Places Search API Error: {}", e.getMessage());
                return Mono.just(Collections.emptyList());
            });
    }

    @SuppressWarnings("unchecked")
    private String extractComponent(List<Map<String, Object>> components, String type) {
        if (components == null) return "Unknown";
        for (Map<String, Object> comp : components) {
            List<String> types = (List<String>) comp.get("types");
            if (types != null && types.contains(type)) {
                return (String) comp.get("short_name");
            }
        }
        return "Unknown";
    }
}