package com.tripplanning.externalinfo.ApiProxyServices;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.tripplanning.externalinfo.dto.ExternalInfoDto.Tour;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class ViatorApi {

    private final WebClient webClient;

    @Value("${external-api.viator.api-key:}")
    private String viatorApiKey;

    public ViatorApi(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @Cacheable(value = "tours", key = "#location + '-' + #countryCode")
    public Mono<List<Tour>> getViatorTours(String location, String countryCode) {
        Map<String, Object> requestBody = Map.of(
                "filtering", Map.of("destination", "648"),
                "currency", "EUR");

        return webClient.post()
                .uri("https://api.sandbox.viator.com/partner/products/search")
                .header("exp-api-key", viatorApiKey != null ? viatorApiKey : "")
                .header("Accept", "application/json;version=2.0")
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> products = (List<Map<String, Object>>) response.get("products");
                    List<Tour> tours = new ArrayList<>();
                    if (products != null) {
                        for (Map<String, Object> p : products) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> pricing = (Map<String, Object>) p.get("pricing");
                            @SuppressWarnings("unchecked")
                            Map<String, Object> summary =
                                    (pricing != null) ? (Map<String, Object>) pricing.get("summary") : null;
                            Object minPrice = (summary != null) ? summary.get("minPrice") : "0.00";
                            tours.add(new Tour(
                                    p.get("productCode").toString(),
                                    p.get("title").toString(),
                                    minPrice.toString() + " €",
                                    p.get("productUrl") != null ? p.get("productUrl").toString() : "#"));
                        }
                    }
                    return tours;
                })
                .onErrorResume(e -> {
                    log.warn("Viator API error for {}: {}", location, e.getMessage());
                    return Mono.just(List.of(new Tour("MOCK", "Discovery Tour " + location, "0.00 €", "#")));
                });
    }
}
