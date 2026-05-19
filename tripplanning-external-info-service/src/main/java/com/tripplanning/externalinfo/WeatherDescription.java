package com.tripplanning.externalinfo;

public enum WeatherDescription {

    CLEAR_SKY(0, "Clear sky"),
    MAINLY_CLEAR(1, "Mainly clear"),
    PARTLY_CLOUDY(2, "Partly cloudy"),
    OVERCAST(3, "Overcast"),
    FOG(45, "Fog"),
    DEPOSITING_RIME_FOG(48, "Depositing rime fog"),
    DRIZZLE_LIGHT(51, "Light drizzle"),
    DRIZZLE_MODERATE(53, "Moderate drizzle"),
    DRIZZLE_DENSE(55, "Dense drizzle"),
    FREEZING_DRIZZLE_LIGHT(56, "Light freezing drizzle"),
    FREEZING_DRIZZLE_DENSE(57, "Dense freezing drizzle"),
    RAIN_SLIGHT(61, "Slight rain"),
    RAIN_MODERATE(63, "Moderate rain"),
    RAIN_HEAVY(65, "Heavy rain"),
    FREEZING_RAIN_LIGHT(66, "Light freezing rain"),
    FREEZING_RAIN_HEAVY(67, "Heavy freezing rain"),
    SNOW_SLIGHT(71, "Slight snow fall"),
    SNOW_MODERATE(73, "Moderate snow fall"),
    SNOW_HEAVY(75, "Heavy snow fall"),
    SNOW_GRAINS(77, "Snow grains"),
    RAIN_SHOWERS_SLIGHT(80, "Slight rain showers"),
    RAIN_SHOWERS_MODERATE(81, "Moderate rain showers"),
    RAIN_SHOWERS_VIOLENT(82, "Violent rain showers"),
    SNOW_SHOWERS_SLIGHT(85, "Slight snow showers"),
    SNOW_SHOWERS_HEAVY(86, "Heavy snow showers"),
    THUNDERSTORM_SLIGHT(95, "Slight or moderate thunderstorm"),
    THUNDERSTORM_HAIL_SLIGHT(96, "Thunderstorm with slight hail"),
    THUNDERSTORM_HAIL_HEAVY(99, "Thunderstorm with heavy hail");

    private final int code;
    private final String description;

    WeatherDescription(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static String getDescriptionByCode(int code) {
        for (WeatherDescription wd : values()) {
            if (wd.code == code) {
                return wd.description;
            }
        }
        return "Unknown weather condition";
    }
}
