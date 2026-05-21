package com.tripplanning.transport;

public record TransportCreatedResponse(
        long id,
        String startGooglePlaceId,
        String endGooglePlaceId,
        String startAddress,
        String endAddress) {

    public static TransportCreatedResponse from(TransportEntity entity) {
        return new TransportCreatedResponse(
                entity.getId(),
                entity.getStartGooglePlaceId(),
                entity.getEndGooglePlaceId(),
                entity.getStartAddress(),
                entity.getEndAddress());
    }
}
