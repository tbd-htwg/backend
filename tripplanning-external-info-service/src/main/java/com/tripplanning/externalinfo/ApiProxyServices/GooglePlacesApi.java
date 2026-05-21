package com.tripplanning.externalinfo.ApiProxyServices;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.tripplanning.externalinfo.dto.ExternalInfoDtos.PlaceDetailsResult;
import com.tripplanning.externalinfo.dto.ExternalInfoDtos.PlaceSearchResult;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class GooglePlacesApi {

    private static final String SEARCH_FIELD_MASK =
            "places.id,places.displayName,places.formattedAddress,places.location";
    private static final String DETAILS_FIELD_MASK =
            "id,displayName,formattedAddress,location,addressComponents";

    private final WebClient webClient;

    @Value("${external-api.google.maps.api-key}")
    private String apiKey;

    @Value("${external-api.google.maps.base-url}")
    private String baseUrl;

    public GooglePlacesApi(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /** Uncached Google Places details (write path and cache miss). */
    public Mono<PlaceDetailsResult> fetchPlaceDetailsUncached(String placeId) {
        requireConfiguredKey();
        String normalizedId = normalizePlaceId(placeId);
        return webClient
                .get()
                .uri(baseUrl + "/places/{placeId}", normalizedId)
                .header("X-Goog-Api-Key", apiKey)
                .header("X-Goog-FieldMask", DETAILS_FIELD_MASK)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(this::mapPlaceDetails)
                .onErrorMap(this::wrapApiError);
    }

    public Mono<List<PlaceSearchResult>> searchLocations(String query) {
        requireConfiguredKey();
        String textQuery = query == null ? "" : query.trim();
        if (textQuery.isEmpty()) {
            return Mono.just(List.of());
        }
        return webClient
                .post()
                .uri(baseUrl + "/places:searchText")
                .header("X-Goog-Api-Key", apiKey)
                .header("X-Goog-FieldMask", SEARCH_FIELD_MASK)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("textQuery", textQuery))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(this::mapSearchResults)
                .onErrorMap(this::wrapApiError);
    }

    @SuppressWarnings("unchecked")
    private List<PlaceSearchResult> mapSearchResults(Map<String, Object> response) {
        List<Map<String, Object>> places = (List<Map<String, Object>>) response.get("places");
        List<PlaceSearchResult> searchResults = new ArrayList<>();
        if (places == null) {
            return searchResults;
        }
        for (Map<String, Object> place : places) {
            PlaceSearchResult hit = mapSearchHit(place);
            if (hit != null) {
                searchResults.add(hit);
            }
        }
        return searchResults;
    }

    @SuppressWarnings("unchecked")
    private PlaceSearchResult mapSearchHit(Map<String, Object> place) {
        String placeId = (String) place.get("id");
        if (placeId == null || placeId.isBlank()) {
            return null;
        }
        String placeName = textFromDisplayName(place.get("displayName"));
        String formattedAddress = (String) place.get("formattedAddress");
        Map<String, Object> location = (Map<String, Object>) place.get("location");
        if (location == null) {
            return null;
        }
        Double lat = numberAsDouble(location.get("latitude"));
        Double lon = numberAsDouble(location.get("longitude"));
        if (lat == null || lon == null) {
            return null;
        }
        return new PlaceSearchResult(
                placeId,
                placeName != null ? placeName : "",
                formattedAddress != null ? formattedAddress : "",
                lat,
                lon);
    }

    @SuppressWarnings("unchecked")
    private PlaceDetailsResult mapPlaceDetails(Map<String, Object> place) {
        if (place == null || place.isEmpty()) {
            return null;
        }
        String placeName = textFromDisplayName(place.get("displayName"));
        String formattedAddress = (String) place.get("formattedAddress");
        Map<String, Object> location = (Map<String, Object>) place.get("location");
        if (location == null) {
            return null;
        }
        Double lat = numberAsDouble(location.get("latitude"));
        Double lon = numberAsDouble(location.get("longitude"));
        if (lat == null || lon == null) {
            return null;
        }
        List<Map<String, Object>> components = (List<Map<String, Object>>) place.get("addressComponents");
        String cityName = extractComponent(components, "locality");
        if ("Unknown".equals(cityName) || cityName.isEmpty()) {
            cityName = extractComponent(components, "postal_town");
        }
        if ("Unknown".equals(cityName) || cityName.isEmpty()) {
            cityName = placeName != null ? placeName : "Unknown";
        }
        String countryCode = extractComponent(components, "country");
        return new PlaceDetailsResult(
                placeName != null ? placeName : "",
                cityName,
                formattedAddress != null ? formattedAddress : "",
                lat,
                lon,
                countryCode.toUpperCase());
    }

    @SuppressWarnings("unchecked")
    private String extractComponent(List<Map<String, Object>> components, String type) {
        if (components == null) {
            return "Unknown";
        }
        for (Map<String, Object> comp : components) {
            List<String> types = (List<String>) comp.get("types");
            if (types != null && types.contains(type)) {
                String shortText = (String) comp.get("shortText");
                if (shortText != null && !shortText.isBlank()) {
                    return shortText;
                }
                String longText = (String) comp.get("longText");
                if (longText != null && !longText.isBlank()) {
                    return longText;
                }
            }
        }
        return "Unknown";
    }

    @SuppressWarnings("unchecked")
    private String textFromDisplayName(Object displayName) {
        if (displayName instanceof Map<?, ?> map) {
            return (String) map.get("text");
        }
        return null;
    }

    private Double numberAsDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    private String normalizePlaceId(String placeId) {
        if (placeId == null) {
            return "";
        }
        if (placeId.startsWith("places/")) {
            return placeId.substring("places/".length());
        }
        return placeId;
    }

    private void requireConfiguredKey() {
        if (apiKey == null || apiKey.isBlank() || "missing_google_key".equals(apiKey)) {
            throw new GooglePlacesApiException(
                    "GOOGLE_MAPS_API_KEY is not configured (set in external-info-service secrets / .env)");
        }
    }

    private Throwable wrapApiError(Throwable error) {
        if (error instanceof GooglePlacesApiException) {
            return error;
        }
        if (error instanceof WebClientResponseException webError) {
            log.error(
                    "Google Places API (New) HTTP {}: {}",
                    webError.getStatusCode(),
                    webError.getResponseBodyAsString());
            return new GooglePlacesApiException(
                    "Google Places API request failed: " + webError.getStatusCode(), webError);
        }
        log.error("Google Places API (New) error: {}", error.getMessage());
        return new GooglePlacesApiException("Google Places API request failed", error);
    }
}
