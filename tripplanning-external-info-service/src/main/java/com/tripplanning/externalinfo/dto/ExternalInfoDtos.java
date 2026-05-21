package com.tripplanning.externalinfo.dto;

import java.math.BigDecimal;
import java.util.List;

public final class ExternalInfoDtos {

    private ExternalInfoDtos() {}

    public record PlaceDetailsResult(
        String placeName,
        String cityName,
        String formattedAddress,
        double lat,
        double lon,
        String countryCode
    ) {}

    /** Compact search hit with explicit Google place id (details use {@link PlaceDetailsResult}). */
    public record PlaceSearchResult(
            String placeId,
            String placeName,
            String formattedAddress,
            double lat,
            double lon) {}

    public record WeatherData(
            double currentTemp,
            int currentWeatherCode,
            String currentDescription,
            /** ISO-8601 timestamp from Open-Meteo {@code current_weather.time} (location local time). */
            String observedAt,
            List<DailyForecast> dailyForecasts) {}

    public record DailyForecast(
            /** ISO date {@code yyyy-MM-dd} for the forecast day. */
            String date,
            double tempMax,
            double tempMin,
            int weatherCode,
            String description) {}

    public record TravelWarning(
            String countryCode,
            String countryName,
            String status,
            String summary,
            String infoUrl) {}

    /** Viator activity exposed to the UI (plain title, formatted price, booking URL). */
    public record Tour(String id, String title, String price, String url) {}

    /** @deprecated Use {@link StopExternalInfo} for trip stops. */
    public record TripExternalInfo(
            TravelWarning warning,
            WeatherData weather,
            List<Tour> tours) {}

    public record StopExternalInfo(TravelWarning warning, WeatherData weather) {}

    public record AccommodationExternalInfo(List<Tour> similarPriceTours, List<Tour> otherTours) {}

    public record TransportDistanceLeg(
            String mode,
            int distanceMeters,
            int durationSeconds,
            String distanceText,
            String durationText) {}

    public record TransportDistanceResult(List<TransportDistanceLeg> legs) {}

    public record AccommodationExternalInput(
            String key,
            String placeId,
            Double lat,
            Double lon,
            String countryCode,
            String cityName,
            BigDecimal cost,
            String currency) {}
}
