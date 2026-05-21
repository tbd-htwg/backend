package com.tripplanning.accommodation;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AccomCreatedResponse(
        long id,
        String type,
        String name,
        String address,
        String cityName,
        String googlePlaceId,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        BigDecimal cost,
        String currency) {

    public static AccomCreatedResponse from(AccomEntity entity) {
        return new AccomCreatedResponse(
                entity.getId(),
                entity.getType(),
                entity.getName(),
                entity.getAddress(),
                entity.getCityName(),
                entity.getGooglePlaceId(),
                entity.getCheckInDate(),
                entity.getCheckOutDate(),
                entity.getCost(),
                entity.getCurrency());
    }
}
