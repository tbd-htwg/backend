package com.tripplanning.transport;

import jakarta.validation.constraints.NotBlank;

public final class TransportRequest {

    private TransportRequest() {}

    public record CreateTransportRequest(
            @NotBlank String startGooglePlaceId, @NotBlank String endGooglePlaceId) {}

    public record UpdateTransportRequest(
            @NotBlank String startGooglePlaceId, @NotBlank String endGooglePlaceId) {}
}
