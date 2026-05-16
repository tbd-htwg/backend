package com.tripplanning.external;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v2/external")
@RequiredArgsConstructor
public class ExternalGatewayController {

    private final ExternalInfoClient externalInfoClient;

    @GetMapping("/details")
    public Mono<ResponseEntity<ExternalInfoDtos.TripExternalInfo>> getExternalDetails(
            @RequestParam String location,
            @RequestParam String countryCode,
            @RequestParam(defaultValue = "0") double lat,
            @RequestParam(defaultValue = "0") double lon) {
        return externalInfoClient
                .fetchExternalDetailsForLocation(buildLocationStub(location, countryCode, lat, lon))
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError().build()));
    }

    private static com.tripplanning.location.LocationEntity buildLocationStub(
            String city, String countryCode, double lat, double lon) {
        com.tripplanning.location.LocationEntity loc = new com.tripplanning.location.LocationEntity(city);
        loc.setCountryCode(countryCode);
        loc.setLatitude(lat);
        loc.setLongitude(lon);
        return loc;
    }
}
