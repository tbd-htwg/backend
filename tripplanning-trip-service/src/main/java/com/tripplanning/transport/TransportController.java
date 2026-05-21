package com.tripplanning.transport;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v2/transports")
@RequiredArgsConstructor
public class TransportController {

    private final TransportService transportService;

    @PostMapping
    public ResponseEntity<TransportCreatedResponse> createTransport(
            @Valid @RequestBody TransportRequest.CreateTransportRequest request) {
        return ResponseEntity.ok(transportService.createTransport(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransportCreatedResponse> updateTransport(
            @PathVariable long id, @Valid @RequestBody TransportRequest.UpdateTransportRequest request) {
        return ResponseEntity.ok(transportService.updateTransport(id, request));
    }
}
