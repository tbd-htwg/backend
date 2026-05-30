package com.tripplanning.externalinfo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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
                .onErrorResume(this::resumeExternalApiError);
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
                .onErrorResume(this::resumeExternalApiError);
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
                .onErrorResume(this::resumeExternalApiError);
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
                .onErrorResume(this::resumeExternalApiError);
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
                .onErrorResume(this::resumeExternalApiError);
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
                .onErrorResume(this::resumeExternalApiError);
    }

    @SuppressWarnings("unchecked")
    private <T> Mono<ResponseEntity<T>> resumeExternalApiError(Throwable error) {
        if (error instanceof ResponseStatusException rse) {
            return Mono.error(rse);
        }
        if (error instanceof GooglePlacesApiException gpe) {
            return Mono.error(placesUnavailable(gpe));
        }
        return Mono.just((ResponseEntity<T>) ResponseEntity.internalServerError().build());
    }

    private static ResponseStatusException placesUnavailable(GooglePlacesApiException e) {
        return new ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), e);
    }
}
