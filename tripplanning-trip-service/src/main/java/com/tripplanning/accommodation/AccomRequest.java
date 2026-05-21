package com.tripplanning.accommodation;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public final class AccomRequest {

    private AccomRequest() {}

    public record CreateAccommodationRequest(
            @NotBlank String googlePlaceId,
            @NotNull LocalDate checkInDate,
            @NotNull LocalDate checkOutDate,
            @NotNull BigDecimal cost,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) {}

    public record UpdateAccommodationRequest(
            @NotBlank String googlePlaceId,
            @NotNull LocalDate checkInDate,
            @NotNull LocalDate checkOutDate,
            @NotNull BigDecimal cost,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) {}
}
