package com.tripplanning.externalinfo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.tripplanning.externalinfo.ApiProxyServices.CachedGooglePlacesService;
import com.tripplanning.externalinfo.ApiProxyServices.GooglePlacesApi;
import com.tripplanning.externalinfo.ApiProxyServices.GooglePlacesApiException;
import com.tripplanning.externalinfo.dto.ExternalInfoDtos.PlaceDetailsResult;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * Service-to-service Google place enrichment (trip-service). Not exposed on public ingress paths
 * that require a user JWT; protected by {@link com.tripplanning.externalinfo.config.InternalApiWebFilter}.
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalExternalApiController {

    private final GooglePlacesApi googlePlacesApi;
    private final CachedGooglePlacesService cachedGooglePlacesService;

    @GetMapping("/location-pack")
    public Mono<ResponseEntity<PlaceDetailsResult>> getLocationPack(
            @RequestParam String placeId, @RequestParam(defaultValue = "false") boolean fresh) {
        Mono<PlaceDetailsResult> details =
                fresh
                        ? googlePlacesApi.fetchPlaceDetailsUncached(placeId)
                        : cachedGooglePlacesService.getPlaceDetailsCached(placeId);
        return details
                .flatMap(
                        geo -> {
                            if (geo == null) {
                                return Mono.just(ResponseEntity.notFound().<PlaceDetailsResult>build());
                            }
                            return Mono.just(ResponseEntity.ok(geo));
                        })
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
                .onErrorResume(GooglePlacesApiException.class, e -> Mono.error(placesUnavailable(e)));
    }

    private static ResponseStatusException placesUnavailable(GooglePlacesApiException e) {
        return new ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), e);
    }
}
