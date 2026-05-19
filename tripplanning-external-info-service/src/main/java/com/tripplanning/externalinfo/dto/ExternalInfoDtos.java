package com.tripplanning.externalinfo.dto;

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

    public record WeatherData(
            double currentTemp,
            int currentWeatherCode,
            String currentDescription,
            List<DailyForecast> dailyForecasts) {}

    public record DailyForecast(
            String date,
            double tempMax,
            double tempMin,
            int weatherCode,
            String description) {}

    public record TravelWarning(
        String country, 
        String status, 
        String message) {}

    public record Tour(
        String id, 
        String title, 
        String description,
        int totalReviews,
        double combinedAverageRating,
        double fromPrice,
        String currency,
        String productUrl) {}

    public record TripExternalInfo(
            TravelWarning warning,
            WeatherData weather,
            List<Tour> tours) {}
}
