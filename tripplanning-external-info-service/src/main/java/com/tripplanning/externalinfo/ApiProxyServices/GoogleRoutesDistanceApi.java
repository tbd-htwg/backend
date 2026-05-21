package com.tripplanning.externalinfo.ApiProxyServices;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.tripplanning.externalinfo.dto.ExternalInfoDtos.TransportDistanceLeg;
import com.tripplanning.externalinfo.dto.ExternalInfoDtos.TransportDistanceResult;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class GoogleRoutesDistanceApi {

    private static final String ROUTES_FIELD_MASK = "routes.distanceMeters,routes.duration,routes.travelAdvisory";

    private final WebClient webClient;

    @Value("${external-api.google.maps.api-key}")
    private String apiKey;

    @Value("${external-api.google.routes.base-url:https://routes.googleapis.com/directions/v2:computeRoutes}")
    private String routesBaseUrl;

    public GoogleRoutesDistanceApi(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @Cacheable(
            value = "transportDistance",
            key =
                    "T(java.lang.String).format('%.4f,%.4f-%.4f,%.4f', #originLat, #originLon, #destLat, #destLon)")
    public Mono<TransportDistanceResult> computeDistance(double originLat, double originLon, double destLat, double destLon) {
        requireConfiguredKey();
        if (!hasValidCoords(originLat, originLon) || !hasValidCoords(destLat, destLon)) {
            return Mono.just(new TransportDistanceResult(List.of()));
        }
        return reactor.core.publisher.Flux.fromArray(new String[] {"DRIVE", "TRANSIT", "WALK"})
                .flatMap(mode -> computeRouteForMode(originLat, originLon, destLat, destLon, mode))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .collectList()
                .map(TransportDistanceResult::new);
    }

    private Mono<java.util.Optional<TransportDistanceLeg>> computeRouteForMode(
            double originLat, double originLon, double destLat, double destLon, String travelMode) {
        Map<String, Object> body = buildRouteRequest(originLat, originLon, destLat, destLon, travelMode);

        return webClient
                .post()
                .uri(routesBaseUrl)
                .header("X-Goog-Api-Key", apiKey)
                .header("X-Goog-FieldMask", ROUTES_FIELD_MASK)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .map(response -> java.util.Optional.ofNullable(mapRouteLeg(response, travelMode)))
                .onErrorResume(
                        WebClientResponseException.class,
                        e -> {
                            log.warn(
                                    "Google Routes API {} failed: {} {}",
                                    travelMode,
                                    e.getStatusCode(),
                                    e.getResponseBodyAsString());
                            return Mono.just(java.util.Optional.empty());
                        })
                .onErrorResume(
                        e -> {
                            log.warn("Google Routes API {} error: {}", travelMode, e.getMessage());
                            return Mono.just(java.util.Optional.empty());
                        });
    }

    private static Map<String, Object> buildRouteRequest(
            double originLat, double originLon, double destLat, double destLon, String travelMode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(
                "origin",
                Map.of(
                        "location",
                        Map.of(
                                "latLng",
                                Map.of("latitude", originLat, "longitude", originLon))));
        body.put(
                "destination",
                Map.of(
                        "location",
                        Map.of(
                                "latLng",
                                Map.of("latitude", destLat, "longitude", destLon))));
        body.put("travelMode", travelMode);
        // TRAFFIC_AWARE is only valid for DRIVE/TWO_WHEELER; including it on TRANSIT makes the API fail.
        if ("DRIVE".equals(travelMode)) {
            body.put("routingPreference", "TRAFFIC_AWARE");
        }
        if ("TRANSIT".equals(travelMode)) {
            body.put("departureTime", Instant.now().toString());
        }
        return body;
    }

    @SuppressWarnings("unchecked")
    private TransportDistanceLeg mapRouteLeg(Map<String, Object> response, String travelMode) {
        if (response == null) {
            return null;
        }
        List<Map<String, Object>> routes = (List<Map<String, Object>>) response.get("routes");
        if (routes == null || routes.isEmpty()) {
            return null;
        }
        Map<String, Object> route = routes.get(0);
        Integer distanceMeters = numberAsInt(route.get("distanceMeters"));
        Integer durationSeconds = parseDurationSeconds(route.get("duration"));
        if (distanceMeters == null || durationSeconds == null) {
            return null;
        }
        return new TransportDistanceLeg(
                travelMode,
                distanceMeters,
                durationSeconds,
                formatDistance(distanceMeters),
                formatDuration(durationSeconds));
    }

    private static Integer parseDurationSeconds(Object durationObj) {
        if (durationObj instanceof String durationStr && durationStr.endsWith("s")) {
            try {
                return Integer.parseInt(durationStr.substring(0, durationStr.length() - 1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (durationObj instanceof Map<?, ?> durationMap) {
            Object seconds = durationMap.get("seconds");
            if (seconds instanceof Number number) {
                return number.intValue();
            }
        }
        return null;
    }

    private static Integer numberAsInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private static String formatDistance(int meters) {
        if (meters >= 1000) {
            return String.format(Locale.ROOT, "%.1f km", meters / 1000.0);
        }
        return meters + " m";
    }

    private static String formatDuration(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%d h %d min", hours, minutes);
        }
        return minutes + " min";
    }

    private static boolean hasValidCoords(double lat, double lon) {
        return (lat != 0 || lon != 0) && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
    }

    private void requireConfiguredKey() {
        if (apiKey == null || apiKey.isBlank() || "missing_google_key".equals(apiKey)) {
            throw new GooglePlacesApiException(
                    "GOOGLE_MAPS_API_KEY is not configured (set in external-info-service secrets / .env)");
        }
    }
}
