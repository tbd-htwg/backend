package com.tripplanning.externalinfo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tripplanning.externalinfo.ApiProxyServices.GeocodingApi;
import com.tripplanning.externalinfo.ApiProxyServices.TravelWarningApi;
import com.tripplanning.externalinfo.ApiProxyServices.ViatorApi;
import com.tripplanning.externalinfo.ApiProxyServices.WeatherApi;
import java.util.List;

import com.tripplanning.externalinfo.dto.ExternalInfoDto.GeocodingResult;
import com.tripplanning.externalinfo.dto.ExternalInfoDto.TripExternalInfo;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/** Public façade for the SPA ({@code /api/v2/external/*}) — routed by the edge gateway. */
@RestController
@RequestMapping("/api/v2/external")
@RequiredArgsConstructor
public class ExternalPublicApiController {

    private final GeocodingApi geocodingApi;
    private final TravelWarningApi travelWarningApi;
    private final WeatherApi weatherApi;
    private final ViatorApi viatorApi;

    @GetMapping("/details/search")
    public Mono<ResponseEntity<List<GeocodingResult>>> search(@RequestParam String q) {
        return geocodingApi.searchLocations(q).map(ResponseEntity::ok);
    }

    @GetMapping("/details")
    public Mono<ResponseEntity<TripExternalInfo>> getExternalDetails(
            @RequestParam String location,
            @RequestParam String countryCode,
            @RequestParam(defaultValue = "0") double lat,
            @RequestParam(defaultValue = "0") double lon) {
        return Mono.zip(
                        travelWarningApi.getTravelWarning(countryCode),
                        weatherApi.getWeather(lat, lon),
                        viatorApi.getViatorTours(location, countryCode))
                .map(tuple -> new TripExternalInfo(tuple.getT1(), tuple.getT2(), tuple.getT3()))
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError().build()));
    }
}
