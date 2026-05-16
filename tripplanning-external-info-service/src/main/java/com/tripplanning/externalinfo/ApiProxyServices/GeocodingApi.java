package com.tripplanning.externalinfo.ApiProxyServices;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.tripplanning.externalinfo.dto.ExternalInfoDto.GeocodingResult;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class GeocodingApi {

    private final WebClient webClient;

    public GeocodingApi(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public Mono<GeocodingResult> searchLocation(String query) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("nominatim.openstreetmap.org")
                        .path("/search")
                        .queryParam("q", query)
                        .queryParam("format", "json")
                        .queryParam("addressdetails", 1)
                        .queryParam("limit", 1)
                        .build())
                .header("User-Agent", "TripPlannerProject/1.0")
                .retrieve()
                .bodyToMono(List.class)
                .flatMap(list -> {
                    if (list.isEmpty()) {
                        return Mono.empty();
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> firstResult = (Map<String, Object>) list.get(0);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> address = (Map<String, Object>) firstResult.get("address");

                    String city = "Unknown";
                    if (address != null) {
                        if (address.containsKey("city")) {
                            city = (String) address.get("city");
                        } else if (address.containsKey("town")) {
                            city = (String) address.get("town");
                        } else if (address.containsKey("village")) {
                            city = (String) address.get("village");
                        } else if (address.containsKey("municipality")) {
                            city = (String) address.get("municipality");
                        }
                    }

                    String displayName = (String) firstResult.get("display_name");
                    double lat = Double.parseDouble(firstResult.get("lat").toString());
                    double lon = Double.parseDouble(firstResult.get("lon").toString());
                    String countryCode = address != null ? (String) address.get("country_code") : "DE";

                    log.info("Geocoding successful for {}: {}, {}", city, lat, lon);
                    return Mono.just(new GeocodingResult(city, displayName, lat, lon, countryCode.toUpperCase()));
                })
                .onErrorResume(e -> {
                    log.error("Geocoding error: {}", e.getMessage());
                    return Mono.empty();
                });
    }
}
