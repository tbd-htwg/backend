package com.tripplanning.externalinfo.ApiProxyServices;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.tripplanning.externalinfo.dto.ExternalInfoDtos.TravelWarning;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class TravelWarningApi {

    private final WebClient webClient;
    private final Map<String, String> dynamicCountryMap = new ConcurrentHashMap<>();

    public TravelWarningApi(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    private Mono<Map<String, String>> refreshCountryMap() {
        if (!dynamicCountryMap.isEmpty()) {
            return Mono.just(dynamicCountryMap);
        }

        return webClient.get()
                .uri("https://www.auswaertiges-amt.de/opendata/travelwarning")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> {
                    JsonNode rows = node.path("response");
                    if (rows.isObject()) {
                        rows.fields().forEachRemaining(entry -> {
                            String id = entry.getKey();
                            JsonNode countryData = entry.getValue();
                            String isoCode = countryData.path("countryCode").asText();
                            if (!isoCode.isBlank()) {
                                dynamicCountryMap.put(isoCode.toUpperCase(), id);
                            }
                        });
                    }
                    log.info("Dynamic mapping loaded: {} countries", dynamicCountryMap.size());
                    return dynamicCountryMap;
                });
    }

    @Cacheable(value = "warnings", key = "#countryCode")
    public Mono<TravelWarning> getTravelWarning(String countryCode) {
        return refreshCountryMap().flatMap(map -> {
            String aaId = map.get(countryCode.toUpperCase());
            if (aaId == null) {
                return Mono.just(new TravelWarning(countryCode, "Info", "No data found."));
            }

            return webClient.get()
                    .uri("https://www.auswaertiges-amt.de/opendata/travelwarning/" + aaId)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .map(node -> {
                        JsonNode data = node.path("response").path(aaId);
                        boolean isWarning = data.path("warning").asBoolean();
                        String rawMsg = isWarning ? data.path("content").asText() : data.path("title").asText();
                        String cleanMsg = rawMsg.replaceAll("<[^>]*>", "");
                        String shortMsg = truncateAtSentenceEnd(cleanMsg, 250);
                        String aaUrl = "https://www.auswaertiges-amt.de/de/service/laender/display-node/id-" + aaId;
                        String finalMessage = shortMsg + " More info: " + aaUrl;
                        return new TravelWarning(
                                countryCode, isWarning ? "Warning" : "Safety Info", finalMessage);
                    });
        });
    }

    private String truncateAtSentenceEnd(String message, int maxLength) {
        if (message == null || message.length() <= maxLength) {
            return message;
        }
        String sub = message.substring(0, maxLength);
        int lastDot = sub.lastIndexOf(".");
        if (lastDot > 50) {
            return sub.substring(0, lastDot + 1);
        }
        int lastSpace = sub.lastIndexOf(" ");
        return (lastSpace > 0 ? sub.substring(0, lastSpace) : sub) + "...";
    }
}
