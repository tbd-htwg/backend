package com.tripplanning.externalinfo.ApiProxyServices;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanning.externalinfo.dto.ExternalInfoDtos.Tour;
import com.tripplanning.externalinfo.util.HtmlText;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class ViatorApi {

    private final WebClient webClient;

    @Value("${external-api.viator.api-key:}")
    private String viatorApiKey;

    @Value("${external-api.viator.base-url}")
    private String baseUrl;

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    
    // In-Memory Speicher für die geladenen IDs aus der JSON-Datei
    private Map<String, String> viatorDestinations = Collections.emptyMap();

    public ViatorApi(WebClient.Builder webClientBuilder, ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initDestinations() {
        try {
            Resource resource = resourceLoader.getResource("classpath:viator-destinations.json");
            try (InputStream inputStream = resource.getInputStream()) {
                viatorDestinations = objectMapper.readValue(inputStream, new TypeReference<Map<String, String>>() {});
                log.info("Viator taxonomy file successfully loaded. {} Stored cities.", viatorDestinations.size());
            }
        } catch (Exception e) {
            log.error("Error: 'viator-destinations.json' could not be loaded.", e);
        }
    }

    @Cacheable(value = "tours", key = "#location + '-' + #countryCode")
    public Mono<List<Tour>> getViatorTours(String location, String countryCode) {
        if (location == null || location.isBlank()) {
            return Mono.just(Collections.emptyList());
        }
        String cleanedLocation = location.toLowerCase().trim();
        String viatorDestinationId = "648"; //Boston als Fallback 
        if (viatorDestinations.containsKey(cleanedLocation)) {
            viatorDestinationId = viatorDestinations.get(cleanedLocation);
            log.info("Viator ID {} for '{}' from JSON file loaded.", viatorDestinationId, location);
        } else {
            log.info("City '{}' not found in JSON file. Use fallback (Boston).", location);
        }
        Map<String, Object> requestBody = Map.of(
                "filtering", Map.of("destination", viatorDestinationId),
                "currency", "EUR");

        return webClient.post()
                .uri(uriBuilder -> UriComponentsBuilder.fromHttpUrl(baseUrl)
                        .path("/partner/products/search") 
                        .build()
                        .toUri())
                .header("exp-api-key", viatorApiKey != null ? viatorApiKey : "")
                .header("Accept", "application/json;version=2.0")
                .header("Content-Type", "application/json")
                .header("Accept-Language", "en-US")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    List<Tour> tours = new ArrayList<>();
                    if (response == null) return tours;

                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> products = (List<Map<String, Object>>) response.get("products");
                    
                    if (products != null) {
                        int limit = Math.min(products.size(), 15);
                        for (int i = 0; i < limit; i++) {
                            Map<String, Object> p = products.get(i);
                            try {
                                // 1. Basis-Informationen extrahieren
                                String title =
                                        HtmlText.strip(
                                                p.get("title") != null
                                                        ? p.get("title").toString()
                                                        : "No title");
                                String productUrl =
                                        p.get("productUrl") != null
                                                ? p.get("productUrl").toString()
                                                : "";

                                // 2. Preise (pricing) auslesen
                                @SuppressWarnings("unchecked")
                                Map<String, Object> pricing = (Map<String, Object>) p.get("pricing");
                                String currency = "EUR";
                                double fromPrice = 0.0;
                                if (pricing != null) {
                                    if (pricing.get("currency") != null) {
                                        currency = pricing.get("currency").toString();
                                    }
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> summary = (Map<String, Object>) pricing.get("summary");
                                    if (summary != null && summary.get("fromPrice") != null) {
                                        fromPrice = ((Number) summary.get("fromPrice")).doubleValue();
                                    }
                                }

                                String priceLabel =
                                        fromPrice > 0
                                                ? String.format("%.2f %s", fromPrice, currency)
                                                : "";
                                String url =
                                        productUrl.isBlank() ? "#" : productUrl;
                                tours.add(
                                        new Tour(
                                                p.get("productCode") != null
                                                        ? p.get("productCode").toString()
                                                        : "UNKNOWN",
                                                title,
                                                priceLabel,
                                                url));

                            } catch (Exception e) {
                                // Falls ein einzelnes Produkt korrupt ist, überspringen wir es, damit die Gesamt-API nicht stirbt!
                                log.error("Error while parsing a single Viator tour: {}", e.getMessage());
                            }
                        }
                    }
                    return tours;
                })
                .onErrorResume(e -> {
                    log.error("Viator API Error: {}", e.getMessage());
                    return Mono.just(
                            List.of(
                                    new Tour(
                                            "MOCK",
                                            "Discovery Tour " + location,
                                            "",
                                            "#")));
                });
    }

    
}