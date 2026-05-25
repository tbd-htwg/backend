package com.tripplanning.externalinfo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.tripplanning.externalinfo.ApiProxyServices.GooglePlacesApi;
import com.tripplanning.externalinfo.ApiProxyServices.GooglePlacesApiException;
import com.tripplanning.externalinfo.dto.ExternalInfoDtos.AccommodationExternalInfo;
import com.tripplanning.externalinfo.dto.ExternalInfoDtos.PlaceSearchResult;
import com.tripplanning.externalinfo.dto.ExternalInfoDtos.StopExternalInfo;
import com.tripplanning.externalinfo.ApiProxyServices.GoogleRoutesDistanceApi;
import com.tripplanning.externalinfo.ApiProxyServices.TransportRouteNotFoundException;
import com.tripplanning.externalinfo.dto.ExternalInfoDtos.TransportRouteResult;
import com.tripplanning.externalinfo.dto.ExternalInfoDtos.TripExternalInfo;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v2/external")
@RequiredArgsConstructor
public class ExternalPublicApiController {

    private final GooglePlacesApi googlePlacesApi;
    private final ExternalDetailsService externalDetailsService;

    @GetMapping("/details/search")
    public Mono<ResponseEntity<List<PlaceSearchResult>>> search(@RequestParam String q) {
        return googlePlacesApi
                .searchLocations(q)
                .map(ResponseEntity::ok)
                .onErrorResume(GooglePlacesApiException.class, e -> Mono.error(placesUnavailable(e)));
    }

    /** @deprecated Prefer {@code /stop-details} for trip stops (no Viator). */
    @GetMapping("/details")
    public Mono<ResponseEntity<TripExternalInfo>> getExternalDetails(
            @RequestParam String placeId,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String cityName) {
        return externalDetailsService
                .tripExternalInfo(placeId, lat, lon, countryCode, cityName)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
                .onErrorResume(GooglePlacesApiException.class, e -> Mono.error(placesUnavailable(e)))
                .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError().build()));
    }

    /** @deprecated Prefer {@code /stop-details/batch}. */
    @GetMapping("/details/batch")
    public Mono<ResponseEntity<Map<String, TripExternalInfo>>> getExternalDetailsBatch(
            @RequestParam String placeIds,
            @RequestParam(required = false) String lats,
            @RequestParam(required = false) String lons,
            @RequestParam(required = false) String countryCodes,
            @RequestParam(required = false) String cityNames) {
        List<ExternalDetailsService.PlaceGeoInput> inputs =
                ExternalDetailsService.parseBatchParams(placeIds, lats, lons, countryCodes, cityNames);
        if (inputs.isEmpty()) {
            return Mono.just(ResponseEntity.ok(Map.of()));
        }
        return externalDetailsService
                .tripExternalInfoBatch(inputs)
                .map(ResponseEntity::ok)
                .onErrorResume(GooglePlacesApiException.class, e -> Mono.error(placesUnavailable(e)))
                .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError().build()));
    }

    @GetMapping("/stop-details")
    public Mono<ResponseEntity<StopExternalInfo>> getStopDetails(
            @RequestParam String placeId,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String cityName) {
        return externalDetailsService
                .stopExternalInfo(placeId, lat, lon, countryCode, cityName)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
                .onErrorResume(GooglePlacesApiException.class, e -> Mono.error(placesUnavailable(e)))
                .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError().build()));
    }

    @GetMapping("/stop-details/batch")
    public Mono<ResponseEntity<Map<String, StopExternalInfo>>> getStopDetailsBatch(
            @RequestParam String placeIds,
            @RequestParam(required = false) String lats,
            @RequestParam(required = false) String lons,
            @RequestParam(required = false) String countryCodes,
            @RequestParam(required = false) String cityNames) {
        List<ExternalDetailsService.PlaceGeoInput> inputs =
                ExternalDetailsService.parseBatchParams(placeIds, lats, lons, countryCodes, cityNames);
        if (inputs.isEmpty()) {
            return Mono.just(ResponseEntity.ok(Map.of()));
        }
        return externalDetailsService
                .stopExternalInfoBatch(inputs)
                .map(ResponseEntity::ok)
                .onErrorResume(GooglePlacesApiException.class, e -> Mono.error(placesUnavailable(e)))
                .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError().build()));
    }

    @GetMapping("/accommodation-details")
    public Mono<ResponseEntity<AccommodationExternalInfo>> getAccommodationDetails(
            @RequestParam String placeId,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String cityName,
            @RequestParam(required = false) BigDecimal cost,
            @RequestParam(required = false) String currency) {
        return externalDetailsService
                .accommodationExternalInfo(placeId, lat, lon, countryCode, cityName, cost, currency)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
                .onErrorResume(GooglePlacesApiException.class, e -> Mono.error(placesUnavailable(e)))
                .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError().build()));
    }

    @GetMapping("/accommodation-details/batch")
    public Mono<ResponseEntity<Map<String, AccommodationExternalInfo>>> getAccommodationDetailsBatch(
            @RequestParam String keys,
            @RequestParam String placeIds,
            @RequestParam(required = false) String lats,
            @RequestParam(required = false) String lons,
            @RequestParam(required = false) String countryCodes,
            @RequestParam(required = false) String cityNames,
            @RequestParam(required = false) String costs,
            @RequestParam(required = false) String currencies) {
        var inputs =
                ExternalDetailsService.parseAccommodationBatchParams(
                        keys, placeIds, lats, lons, countryCodes, cityNames, costs, currencies);
        if (inputs.isEmpty()) {
            return Mono.just(ResponseEntity.ok(Map.of()));
        }
        return externalDetailsService
                .accommodationExternalInfoBatch(inputs)
                .map(ResponseEntity::ok)
                .onErrorResume(GooglePlacesApiException.class, e -> Mono.error(placesUnavailable(e)))
                .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError().build()));
    }

    @GetMapping("/transport/route")
    public Mono<ResponseEntity<TransportRouteResult>> getTransportRoute(
            @RequestParam double originLat,
            @RequestParam double originLon,
            @RequestParam double destLat,
            @RequestParam double destLon,
            @RequestParam String mode) {
        if (GoogleRoutesDistanceApi.normalizeTravelMode(mode) == null) {
            return Mono.error(
                    new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Invalid mode. Allowed: DRIVE, WALK, BICYCLE, TRANSIT"));
        }
        return externalDetailsService
                .transportRoute(originLat, originLon, destLat, destLon, mode)
                .map(ResponseEntity::ok)
                .onErrorMap(
                        TransportRouteNotFoundException.class,
                        e -> new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage()))
                .onErrorMap(
                        IllegalArgumentException.class,
                        e -> new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()))
                .onErrorMap(
                        GooglePlacesApiException.class,
                        ExternalPublicApiController::mapTransportRouteServiceError)
                .onErrorMap(
                        e -> !(e instanceof ResponseStatusException),
                        e -> new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Transport route request failed",
                                e));
    }

    private static ResponseStatusException placesUnavailable(GooglePlacesApiException e) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), e);
    }

    /** Only true outages (missing key, Routes API disabled) are 503; not “no route for this mode”. */
    private static ResponseStatusException mapTransportRouteServiceError(GooglePlacesApiException e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("not configured") || msg.contains("Routes API") || msg.contains("API key")) {
            return placesUnavailable(e);
        }
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, msg, e);
    }
}
