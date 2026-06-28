package com.tripplanning.externalinfo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.tripplanning.externalinfo.ApiProxyServices.CachedGooglePlacesService;
import com.tripplanning.externalinfo.ApiProxyServices.TravelWarningApi;
import com.tripplanning.externalinfo.ApiProxyServices.ViatorApi;
import com.tripplanning.externalinfo.ApiProxyServices.WeatherApi;
import com.tripplanning.externalinfo.dto.ExternalInfoDtos.AccommodationExternalInfo;
import com.tripplanning.externalinfo.dto.ExternalInfoDtos.AccommodationExternalInput;
import com.tripplanning.externalinfo.dto.ExternalInfoDtos.PlaceDetailsResult;
import com.tripplanning.externalinfo.dto.ExternalInfoDtos.StopExternalInfo;
import com.tripplanning.externalinfo.dto.ExternalInfoDtos.TripExternalInfo;
import com.tripplanning.externalinfo.util.ViatorPriceSplitter;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ExternalDetailsService {

    private final CachedGooglePlacesService cachedGooglePlacesService;
    private final TravelWarningApi travelWarningApi;
    private final WeatherApi weatherApi;
    private final ViatorApi viatorApi;

    /** Legacy combined dsdsdpayload (still includes Viator). */
    public Mono<TripExternalInfo> tripExternalInfo(
            String placeId, Double lat, Double lon, String countryCode, String cityName) {
        return resolveGeo(placeId, lat, lon, countryCode, cityName)
                .flatMap(
                        geo ->
                                Mono.zip(
                                                travelWarningApi.getTravelWarning(geo.countryCode()),
                                                weatherApi.getWeather(geo.lat(), geo.lon()),
                                                viatorApi.getViatorTours(geo.cityName(), geo.countryCode()))
                                        .map(
                                                tuple ->
                                                        new TripExternalInfo(
                                                                tuple.getT1(), tuple.getT2(), tuple.getT3())));
    }

    public Mono<Map<String, TripExternalInfo>> tripExternalInfoBatch(List<PlaceGeoInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return Mono.just(Map.of());
        }
        Map<String, PlaceGeoInput> deduped = new LinkedHashMap<>();
        for (PlaceGeoInput input : inputs) {
            deduped.putIfAbsent(input.placeId(), input);
        }
        return Flux.fromIterable(deduped.values())
                .flatMap(
                        input ->
                                tripExternalInfo(
                                                input.placeId(),
                                                input.lat(),
                                                input.lon(),
                                                input.countryCode(),
                                                input.cityName())
                                        .map(info -> Map.entry(input.placeId(), info)))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    public Mono<StopExternalInfo> stopExternalInfo(
            String placeId, Double lat, Double lon, String countryCode, String cityName) {
        return resolveGeo(placeId, lat, lon, countryCode, cityName).flatMap(this::buildStopExternalInfo);
    }

    public Mono<Map<String, StopExternalInfo>> stopExternalInfoBatch(List<PlaceGeoInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return Mono.just(Map.of());
        }
        Map<String, PlaceGeoInput> deduped = new LinkedHashMap<>();
        for (PlaceGeoInput input : inputs) {
            deduped.putIfAbsent(input.placeId(), input);
        }
        return Flux.fromIterable(deduped.values())
                .flatMap(
                        input ->
                                stopExternalInfo(
                                                input.placeId(),
                                                input.lat(),
                                                input.lon(),
                                                input.countryCode(),
                                                input.cityName())
                                        .map(info -> Map.entry(input.placeId(), info)))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    public Mono<AccommodationExternalInfo> accommodationExternalInfo(
            String placeId,
            Double lat,
            Double lon,
            String countryCode,
            String cityName,
            BigDecimal cost,
            String currency) {
        return resolveGeo(placeId, lat, lon, countryCode, cityName)
                .flatMap(geo -> buildAccommodationExternalInfo(geo, cost, currency));
    }

    public Mono<Map<String, AccommodationExternalInfo>> accommodationExternalInfoBatch(
            List<AccommodationExternalInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return Mono.just(Map.of());
        }
        return Flux.fromIterable(inputs)
                .flatMap(
                        input ->
                                accommodationExternalInfo(
                                                input.placeId(),
                                                input.lat(),
                                                input.lon(),
                                                input.countryCode(),
                                                input.cityName(),
                                                input.cost(),
                                                input.currency())
                                        .map(info -> Map.entry(input.key(), info)))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    private Mono<StopExternalInfo> buildStopExternalInfo(PlaceDetailsResult geo) {
        if (geo == null) {
            return Mono.empty();
        }
        return Mono.zip(
                        travelWarningApi.getTravelWarning(geo.countryCode()),
                        weatherApi.getWeather(geo.lat(), geo.lon()))
                .map(tuple -> new StopExternalInfo(tuple.getT1(), tuple.getT2()));
    }

    private Mono<AccommodationExternalInfo> buildAccommodationExternalInfo(
            PlaceDetailsResult geo, BigDecimal cost, String currency) {
        if (geo == null) {
            return Mono.empty();
        }
        return viatorApi
                .getViatorTours(geo.cityName(), geo.countryCode())
                .map(tours -> ViatorPriceSplitter.split(tours, cost, currency));
    }

    private Mono<PlaceDetailsResult> resolveGeo(
            String placeId, Double lat, Double lon, String countryCode, String cityName) {
        if (hasClientGeo(lat, lon, countryCode)) {
            String city = cityName != null && !cityName.isBlank() ? cityName : "";
            return Mono.just(
                    new PlaceDetailsResult(
                            city,
                            city,
                            "",
                            lat,
                            lon,
                            countryCode.trim().toUpperCase(),
                            ""));
        }
        if (placeId == null || placeId.isBlank()) {
            return Mono.empty();
        }
        return cachedGooglePlacesService.getPlaceDetailsCached(placeId);
    }

    private static boolean hasClientGeo(Double lat, Double lon, String countryCode) {
        return lat != null
                && lon != null
                && countryCode != null
                && !countryCode.isBlank();
    }

    public record PlaceGeoInput(
            String placeId, Double lat, Double lon, String countryCode, String cityName) {}

    public static List<PlaceGeoInput> parseBatchParams(
            String placeIdsParam,
            String latsParam,
            String lonsParam,
            String countryCodesParam,
            String cityNamesParam) {
        if (placeIdsParam == null || placeIdsParam.isBlank()) {
            return List.of();
        }
        String[] placeIds = placeIdsParam.split(",");
        String[] lats = splitOrEmpty(latsParam, placeIds.length);
        String[] lons = splitOrEmpty(lonsParam, placeIds.length);
        String[] countries = splitOrEmpty(countryCodesParam, placeIds.length);
        String[] cities = splitOrEmpty(cityNamesParam, placeIds.length);

        List<PlaceGeoInput> inputs = new ArrayList<>(placeIds.length);
        for (int i = 0; i < placeIds.length; i++) {
            String placeId = placeIds[i].trim();
            if (placeId.isEmpty()) {
                continue;
            }
            inputs.add(
                    new PlaceGeoInput(
                            placeId,
                            parseDouble(lats[i]),
                            parseDouble(lons[i]),
                            blankToNull(countries[i]),
                            blankToNull(cities[i])));
        }
        return inputs;
    }

    public static List<AccommodationExternalInput> parseAccommodationBatchParams(
            String keysParam,
            String placeIdsParam,
            String latsParam,
            String lonsParam,
            String countryCodesParam,
            String cityNamesParam,
            String costsParam,
            String currenciesParam) {
        if (keysParam == null || keysParam.isBlank()) {
            return List.of();
        }
        String[] keys = keysParam.split(",", -1);
        String[] placeIds = splitOrEmpty(placeIdsParam, keys.length);
        String[] lats = splitOrEmpty(latsParam, keys.length);
        String[] lons = splitOrEmpty(lonsParam, keys.length);
        String[] countries = splitOrEmpty(countryCodesParam, keys.length);
        String[] cities = splitOrEmpty(cityNamesParam, keys.length);
        String[] costs = splitOrEmpty(costsParam, keys.length);
        String[] currencies = splitOrEmpty(currenciesParam, keys.length);

        List<AccommodationExternalInput> inputs = new ArrayList<>(keys.length);
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i].trim();
            if (key.isEmpty()) {
                continue;
            }
            inputs.add(
                    new AccommodationExternalInput(
                            key,
                            blankToNull(placeIds[i]),
                            parseDouble(lats[i]),
                            parseDouble(lons[i]),
                            blankToNull(countries[i]),
                            blankToNull(cities[i]),
                            parseBigDecimal(costs[i]),
                            blankToNull(currencies[i])));
        }
        return inputs;
    }

    private static String[] splitOrEmpty(String param, int expectedLength) {
        if (param == null || param.isBlank()) {
            return new String[expectedLength];
        }
        return param.split(",", -1);
    }

    private static Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
