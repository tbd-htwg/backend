package com.tripplanning.images;


import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.tripplanning.tripLocation.TripLocationEntity;
import com.tripplanning.tripLocation.TripLocationRepository;

@RestController
@RequestMapping("/api/v2/trip-locations")
@RequiredArgsConstructor
public class TripLocationController {
    private final ImageService imageService;
    private final TripLocationRepository tripLocationRepository; 

    @PostMapping("/{tripLocationId}/images")
    public ResponseEntity<?> createUploadUrl(
            @PathVariable Long tripLocationId, 
            @RequestBody ImageUploadDtos.CreateUploadRequest request) {
        try {
            ImageService.SignedUploadInfo signedUpload =
                    imageService.createSignedUpload(
                            "trip-locations/" + tripLocationId,
                            request.fileName(),
                            request.contentType());

            TripLocationEntity tripLocation = tripLocationRepository.findById(tripLocationId)
                    .orElseThrow(() -> new RuntimeException("Trip-Location nicht gefunden"));

            tripLocation.setImageUrl(signedUpload.objectUrl());
            tripLocationRepository.save(tripLocation);

            return ResponseEntity.ok(
                    new ImageUploadDtos.CreateUploadResponse(
                            signedUpload.uploadUrl(),
                            signedUpload.objectUrl(),
                            signedUpload.objectName(),
                            signedUpload.contentType()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Fehler beim Erstellen der Upload-URL: " + e.getMessage());
        }
    }
}