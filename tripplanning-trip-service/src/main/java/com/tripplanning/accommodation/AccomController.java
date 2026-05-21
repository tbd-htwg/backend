package com.tripplanning.accommodation;

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
@RequestMapping("/api/v2/accommodations")
@RequiredArgsConstructor
public class AccomController {

    private final AccomService accomService;

    @PostMapping
    public ResponseEntity<AccomCreatedResponse> createAccommodation(
            @Valid @RequestBody AccomRequest.CreateAccommodationRequest request) {
        return ResponseEntity.ok(accomService.createAccommodation(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AccomCreatedResponse> updateAccommodation(
            @PathVariable long id, @Valid @RequestBody AccomRequest.UpdateAccommodationRequest request) {
        return ResponseEntity.ok(accomService.updateAccommodation(id, request));
    }
}
