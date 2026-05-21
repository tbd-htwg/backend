package com.tripplanning.external;

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
            String observedAt,
            List<DailyForecast> dailyForecasts) {}

    public record DailyForecast(
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

    public record Tour(
        String id, 
        String title, 
        String price, 
        String url) {}

    public record TripExternalInfo(
            TravelWarning warning,
            WeatherData weather,
            List<Tour> tours) {}
}
