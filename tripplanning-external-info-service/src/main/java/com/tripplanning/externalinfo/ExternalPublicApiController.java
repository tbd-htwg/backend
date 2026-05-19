package com.tripplanning.externalinfo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tripplanning.externalinfo.ApiProxyServices.GooglePlacesApi;
import com.tripplanning.externalinfo.ApiProxyServices.TravelWarningApi;
import com.tripplanning.externalinfo.ApiProxyServices.ViatorApi;
import com.tripplanning.externalinfo.ApiProxyServices.WeatherApi;
import java.util.List;

import com.tripplanning.externalinfo.dto.ExternalInfoDtos.PlaceDetailsResult;
import com.tripplanning.externalinfo.dto.ExternalInfoDtos.TripExternalInfo;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v2/external")
@RequiredArgsConstructor
public class ExternalPublicApiController {

    private final GooglePlacesApi googlePlacesApi;
    private final TravelWarningApi travelWarningApi;
    private final WeatherApi weatherApi;
    private final ViatorApi viatorApi;

    // 1. Für das Frontend: Suchfeld (Google Text Search)
    @GetMapping("/details/search")
    public Mono<ResponseEntity<List<PlaceDetailsResult>>> search(@RequestParam String q) {
        return googlePlacesApi.searchLocations(q)
                .map(ResponseEntity::ok);
    }

    // 2. Für das Frontend: Das komplette Info-Paket (Wetter, Viator, Warnungen)
    @GetMapping("/details")
    public Mono<ResponseEntity<TripExternalInfo>> getExternalDetails(@RequestParam String placeId) {
        return googlePlacesApi.getPlaceDetails(placeId)
            .flatMap(geo -> {
                if (geo == null) {
                    return Mono.just(ResponseEntity.notFound().<TripExternalInfo>build());
                }

                return Mono.zip(
                        travelWarningApi.getTravelWarning(geo.countryCode()),
                        weatherApi.getWeather(geo.lat(), geo.lon()),
                        viatorApi.getViatorTours(geo.cityName(), geo.countryCode())
                )
                .map(tuple -> new TripExternalInfo(tuple.getT1(), tuple.getT2(), tuple.getT3()))
                .map(ResponseEntity::ok);
            })
            .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError().build()));
    }

    // 3. Für den Trip-Service (8080): Nur die Google-Details (Unterkünfte/Stopps)
    @GetMapping("/location-pack")
    public Mono<ResponseEntity<PlaceDetailsResult>> getLocationPack(@RequestParam String placeId) {
    return googlePlacesApi.getPlaceDetails(placeId)
            .flatMap(geo -> {
                if (geo == null) {
                    return Mono.just(ResponseEntity.notFound().<PlaceDetailsResult>build());
                }
                return Mono.just(ResponseEntity.ok(geo));
            })
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}