package com.tripplanning.images;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.tripplanning.tripLocation.TripLocationEntity;
import com.tripplanning.tripLocation.TripLocationRepository;

import lombok.RequiredArgsConstructor;

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

    @DeleteMapping("/{tripLocationId}/images")
    public ResponseEntity<Void> deleteImage(
            @PathVariable Long tripLocationId, @AuthenticationPrincipal Jwt jwt) {
        long callerId = Long.parseLong(jwt.getSubject());
        TripLocationEntity tripLocation =
                tripLocationRepository
                        .findById(tripLocationId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip-Location nicht gefunden"));
        long ownerId = tripLocation.getTrip().getUser().getId();
        if (ownerId != callerId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nicht berechtigt");
        }
        String prefix = "trip-locations/" + tripLocationId + "/";
        imageService.deleteStoredObjectByUrlIfApplicable(tripLocation.getImageUrl(), prefix);
        tripLocation.setImageUrl(null);
        tripLocationRepository.save(tripLocation);
        return ResponseEntity.noContent().build();
    }
}