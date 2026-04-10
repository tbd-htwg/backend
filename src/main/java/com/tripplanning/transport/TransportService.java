package com.tripplanning.transport;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripplanning.api.dto.request.TransportRequest;
import com.tripplanning.api.dto.response.TransportResponse;

import lombok.RequiredArgsConstructor;

import java.util.List;

@Service
@RequiredArgsConstructor 
public class TransportService {
    private final TransportRepository transportRepository;

    @Transactional
    public TransportResponse getOrCreateTransport(TransportRequest request) {
        TransportEntity transport = transportRepository
            .findByType(request.type())
            .orElseGet(() -> transportRepository.save(
                new TransportEntity(request.type())
            ));

        return new TransportResponse(transport.getTransport_id(), transport.getType());
    }

    @Transactional(readOnly = true)
    public List<TransportResponse> listAllTransports() {
        return transportRepository.findAll().stream()
            .map(t -> new TransportResponse(t.getTransport_id(), t.getType()))
            .toList();
    }
}
