package com.tripplanning.transport;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.tripplanning.external.ExternalInfoDtos.PlaceDetailsResult;
import com.tripplanning.external.PlaceEnrichmentHelper;
import com.tripplanning.trip.read.TripCacheEvictor;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransportService {

    private final TransportRepository transportRepository;
    private final PlaceEnrichmentHelper placeEnrichmentHelper;
    private final TripCacheEvictor tripCacheEvictor;

    public TransportCreatedResponse createTransport(TransportRequest.CreateTransportRequest request) {
        PlaceDetailsResult start = placeEnrichmentHelper.requirePlaceDetails(request.startGooglePlaceId());
        PlaceDetailsResult end = placeEnrichmentHelper.requirePlaceDetails(request.endGooglePlaceId());

        TransportEntity entity =
                TransportEntity.builder()
                        .startGooglePlaceId(request.startGooglePlaceId())
                        .endGooglePlaceId(request.endGooglePlaceId())
                        .startAddress(start.formattedAddress())
                        .endAddress(end.formattedAddress())
                        .build();

        return TransportCreatedResponse.from(transportRepository.save(entity));
    }

    public TransportCreatedResponse updateTransport(long id, TransportRequest.UpdateTransportRequest request) {
        TransportEntity entity =
                transportRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transport not found."));

        PlaceDetailsResult start = placeEnrichmentHelper.requirePlaceDetails(request.startGooglePlaceId());
        PlaceDetailsResult end = placeEnrichmentHelper.requirePlaceDetails(request.endGooglePlaceId());

        entity.setStartGooglePlaceId(request.startGooglePlaceId());
        entity.setEndGooglePlaceId(request.endGooglePlaceId());
        entity.setStartAddress(start.formattedAddress());
        entity.setEndAddress(end.formattedAddress());

        TransportCreatedResponse response = TransportCreatedResponse.from(transportRepository.save(entity));
        tripCacheEvictor.evictAllFeeds();
        return response;
    }
}
