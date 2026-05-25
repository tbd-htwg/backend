package com.tripplanning.externalinfo.ApiProxyServices;

public class TransportRouteNotFoundException extends RuntimeException {

    public TransportRouteNotFoundException(String travelMode) {
        super(messageForMode(travelMode));
    }

    public TransportRouteNotFoundException(String travelMode, String detail) {
        super(detail != null && !detail.isBlank() ? detail : messageForMode(travelMode));
    }

    private static String messageForMode(String travelMode) {
        return switch (travelMode) {
            case "BICYCLE" -> "No cycling route for this trip.";
            case "TRANSIT" -> "No transit route for this trip.";
            case "WALK" -> "No walking route for this trip.";
            case "DRIVE" -> "No driving route for this trip.";
            default -> "No route for this travel mode.";
        };
    }
}
