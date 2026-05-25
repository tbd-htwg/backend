package com.tripplanning.externalinfo.ApiProxyServices;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.tripplanning.externalinfo.dto.ExternalInfoDtos.TransportRouteResult;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class GoogleRoutesDistanceApi {

    private static final String ROUTES_FIELD_MASK =
            "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline";

    private static final Set<String> ALLOWED_MODES = Set.of("DRIVE", "WALK", "BICYCLE", "TRANSIT");

    private final WebClient webClient;

    @Value("${external-api.google.maps.api-key}")
    private String apiKey;

    @Value("${external-api.google.routes.base-url:https://routes.googleapis.com/directions/v2:computeRoutes}")
    private String routesBaseUrl;

    public GoogleRoutesDistanceApi(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public static String normalizeTravelMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return null;
        }
        String upper = mode.trim().toUpperCase(Locale.ROOT);
        return ALLOWED_MODES.contains(upper) ? upper : null;
    }

    @Cacheable(
            value = "transportRouteV2",
            key =
                    "T(java.lang.String).format('%s-%.4f,%.4f-%.4f,%.4f', #travelMode, #originLat, #originLon, #destLat, #destLon)")
    public Mono<TransportRouteResult> computeRoute(
            double originLat, double originLon, double destLat, double destLon, String travelMode) {
        requireConfiguredKey();
        String mode = normalizeTravelMode(travelMode);
        if (mode == null) {
            return Mono.error(
                    new IllegalArgumentException(
                            "Invalid travel mode. Allowed: DRIVE, WALK, BICYCLE, TRANSIT"));
        }
        if (!hasValidCoords(originLat, originLon) || !hasValidCoords(destLat, destLon)) {
            return Mono.empty();
        }
        Map<String, Object> body = buildRouteRequest(originLat, originLon, destLat, destLon, mode);

        return webClient
                .post()
                .uri(routesBaseUrl)
                .header("X-Goog-Api-Key", apiKey)
                .header("X-Goog-FieldMask", ROUTES_FIELD_MASK)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .flatMap(
                        response -> {
                            TransportRouteResult result = mapRouteResult(response, mode);
                            if (result != null) {
                                return Mono.just(result);
                            }
                            return Mono.error(new TransportRouteNotFoundException(mode));
                        })
                .onErrorResume(WebClientResponseException.class, e -> mapWebClientError(e, mode))
                .onErrorResume(TransportRouteNotFoundException.class, Mono::error)
                .onErrorResume(
                        DataBufferLimitException.class,
                        e ->
                                Mono.error(
                                        new TransportRouteNotFoundException(
                                                mode,
                                                "Route is too long to display. Try Driving or a shorter trip.")))
                .onErrorResume(
                        e -> !(e instanceof TransportRouteNotFoundException),
                        e -> {
                            log.error("Google Routes API {} unexpected error", mode, e);
                            return Mono.error(new GooglePlacesApiException(routesServiceDownMessage()));
                        });
    }

    private static Mono<TransportRouteResult> mapWebClientError(
            WebClientResponseException e, String mode) {
        HttpStatusCode status = e.getStatusCode();
        String body = e.getResponseBodyAsString();
        log.warn("Google Routes API {} HTTP {}: {}", mode, status.value(), truncate(body, 500));

        if (status.isSameCodeAs(org.springframework.http.HttpStatus.FORBIDDEN)
                || status.isSameCodeAs(org.springframework.http.HttpStatus.UNAUTHORIZED)) {
            return Mono.error(
                    new GooglePlacesApiException(
                            "GOOGLE_MAPS_API_KEY cannot access Routes API (check key restrictions and enable Routes API)."));
        }
        if (status.is5xxServerError()) {
            return Mono.error(new GooglePlacesApiException(routesServiceDownMessage()));
        }
        if (isNoRouteClientError(status, body)) {
            return Mono.error(new TransportRouteNotFoundException(mode));
        }
        return Mono.error(new TransportRouteNotFoundException(mode));
    }

    private static boolean isNoRouteClientError(HttpStatusCode status, String body) {
        if (!status.is4xxClientError()) {
            return false;
        }
        if (body == null || body.isBlank()) {
            return true;
        }
        String lower = body.toLowerCase(Locale.ROOT);
        return lower.contains("route")
                || lower.contains("not found")
                || lower.contains("invalid_argument")
                || lower.contains("no path")
                || lower.contains("zero results");
    }

    private static String routesServiceDownMessage() {
        return "Routes service is temporarily unreachable. Try again later.";
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
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
        body.put("polylineEncoding", "ENCODED_POLYLINE");
        body.put("polylineQuality", "OVERVIEW");
        if ("DRIVE".equals(travelMode)) {
            body.put("routingPreference", "TRAFFIC_AWARE");
        }
        if ("TRANSIT".equals(travelMode)) {
            body.put("departureTime", Instant.now().toString());
        }
        return body;
    }

    @SuppressWarnings("unchecked")
    private TransportRouteResult mapRouteResult(Map<String, Object> response, String travelMode) {
        if (response == null) {
            return null;
        }
        var routes = (java.util.List<Map<String, Object>>) response.get("routes");
        if (routes == null || routes.isEmpty()) {
            return null;
        }
        Map<String, Object> route = routes.get(0);
        Integer distanceMeters = numberAsInt(route.get("distanceMeters"));
        Integer durationSeconds = parseDurationSeconds(route.get("duration"));
        String encodedPolyline = extractEncodedPolyline(route);
        if (distanceMeters == null || durationSeconds == null || encodedPolyline == null) {
            return null;
        }
        return new TransportRouteResult(
                travelMode,
                distanceMeters,
                durationSeconds,
                formatDistance(distanceMeters),
                formatDuration(durationSeconds),
                encodedPolyline);
    }

    @SuppressWarnings("unchecked")
    private static String extractEncodedPolyline(Map<String, Object> route) {
        Object polylineObj = route.get("polyline");
        if (!(polylineObj instanceof Map<?, ?> polyline)) {
            return null;
        }
        Object encoded = polyline.get("encodedPolyline");
        if (encoded instanceof String s && !s.isBlank()) {
            return s;
        }
        return null;
    }

    private static Integer parseDurationSeconds(Object durationObj) {
        if (durationObj instanceof String durationStr && durationStr.endsWith("s")) {
            try {
                long seconds = Long.parseLong(durationStr.substring(0, durationStr.length() - 1));
                if (seconds > Integer.MAX_VALUE) {
                    return Integer.MAX_VALUE;
                }
                return (int) seconds;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (durationObj instanceof Map<?, ?> durationMap) {
            Object seconds = durationMap.get("seconds");
            if (seconds instanceof Number number) {
                long value = number.longValue();
                return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : number.intValue();
            }
        }
        return null;
    }

    private static Integer numberAsInt(Object value) {
        if (value instanceof Number number) {
            long v = number.longValue();
            return v > Integer.MAX_VALUE ? Integer.MAX_VALUE : number.intValue();
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
