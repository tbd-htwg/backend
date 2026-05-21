package com.tripplanning.externalinfo.ApiProxyServices;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.tripplanning.externalinfo.TravelWarningContentParser;
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

        return webClient
                .get()
                .uri("https://www.auswaertiges-amt.de/opendata/travelwarning")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(
                        node -> {
                            JsonNode rows = node.path("response");
                            if (rows.isObject()) {
                                rows.fields()
                                        .forEachRemaining(
                                                entry -> {
                                                    String id = entry.getKey();
                                                    JsonNode countryData = entry.getValue();
                                                    String isoCode =
                                                            countryData
                                                                    .path("countryCode")
                                                                    .asText();
                                                    if (!isoCode.isBlank()) {
                                                        dynamicCountryMap.put(
                                                                isoCode.toUpperCase(), id);
                                                    }
                                                });
                            }
                            log.info(
                                    "Dynamic mapping loaded: {} countries",
                                    dynamicCountryMap.size());
                            return dynamicCountryMap;
                        });
    }

    @Cacheable(value = "warnings", key = "#countryCode")
    public Mono<TravelWarning> getTravelWarning(String countryCode) {
        return refreshCountryMap()
                .flatMap(
                        map -> {
                            String normalizedCode = countryCode.toUpperCase();
                            String aaId = map.get(normalizedCode);
                            if (aaId == null) {
                                return Mono.just(
                                        new TravelWarning(
                                                normalizedCode,
                                                normalizedCode,
                                                "Info",
                                                "No travel advisory available.",
                                                ""));
                            }

                            return webClient
                                    .get()
                                    .uri(
                                            "https://www.auswaertiges-amt.de/opendata/travelwarning/"
                                                    + aaId)
                                    .retrieve()
                                    .bodyToMono(JsonNode.class)
                                    .map(
                                            node -> {
                                                JsonNode data =
                                                        node.path("response").path(aaId);
                                                String title = data.path("title").asText();
                                                String htmlContent =
                                                        data.path("content").asText("");
                                                String summary =
                                                        TravelWarningContentParser.extractSummary(
                                                                htmlContent);
                                                String countryName =
                                                        parseCountryDisplayName(
                                                                title,
                                                                data.path("countryName")
                                                                        .asText());
                                                String status =
                                                        TravelWarningContentParser.resolveStatus(
                                                                data.path("warning").asBoolean(),
                                                                data.path("partialWarning")
                                                                        .asBoolean(),
                                                                data.path("situationWarning")
                                                                        .asBoolean(),
                                                                data.path("situationPartWarning")
                                                                        .asBoolean());
                                                String aaUrl =
                                                        "https://www.auswaertiges-amt.de/de/service/laender/display-node/id-"
                                                                + aaId;
                                                return new TravelWarning(
                                                        normalizedCode,
                                                        countryName,
                                                        status,
                                                        summary,
                                                        aaUrl);
                                            });
                        });
    }

    /**
     * Titles look like {@code USA/Vereinigte Staaten: Reise- und Sicherheitshinweise}; prefer the
     * German segment after {@code /} when present.
     */
    static String parseCountryDisplayName(String title, String fallbackCountryName) {
        if (title != null && title.contains(":")) {
            String beforeColon = title.substring(0, title.indexOf(':')).trim();
            if (beforeColon.contains("/")) {
                String[] parts = beforeColon.split("/", 2);
                if (parts.length == 2 && !parts[1].isBlank()) {
                    return parts[1].trim();
                }
            }
            if (!beforeColon.isBlank()) {
                return beforeColon;
            }
        }
        if (fallbackCountryName != null && !fallbackCountryName.isBlank()) {
            return fallbackCountryName.trim();
        }
        return "Unknown country";
    }
}
