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

import java.util.List;

import com.tripplanning.tripLocation.TripLocationEntity;
import com.tripplanning.tripLocation.TripLocationImageEntity;
import com.tripplanning.tripLocation.TripLocationImageRepository;
import com.tripplanning.tripLocation.TripLocationRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v2/trip-locations")
@RequiredArgsConstructor
public class TripLocationImageController {
    private final ImageService imageService;
    private final TripLocationRepository tripLocationRepository;
    private final TripLocationImageRepository tripLocationImageRepository;

    @PostMapping("/{tripLocationId}/images")
    public ResponseEntity<?> createUploadUrl(
            @PathVariable Long tripLocationId, 
            @RequestBody ImageUploadDtos.CreateUploadRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        try {
            long callerId = Long.parseLong(jwt.getSubject());
            TripLocationEntity tripLocation = tripLocationRepository
                .findByIdWithTripAndUser(tripLocationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found."));
            long ownerId = tripLocation.getTrip().getUser().getId();
            if (ownerId != callerId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not authorized.");
            }

            ImageService.SignedUploadInfo signedUpload =
                    imageService.createSignedUpload(
                            "trip-locations/" + tripLocationId,
                            request.fileName(),
                            request.contentType());

                TripLocationImageEntity image = TripLocationImageEntity.builder()
                    .tripLocation(tripLocation)
                    .imagePath(signedUpload.objectUrl())
                    .build();

                tripLocationImageRepository.save(image);

            return ResponseEntity.ok(
                    new ImageUploadDtos.CreateUploadResponse(
                image.getId(),
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
                        .findByIdWithTripAndUser(tripLocationId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found."));
        long ownerId = tripLocation.getTrip().getUser().getId();
        if (ownerId != callerId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not authorized.");
        }
        List<TripLocationImageEntity> images = tripLocationImageRepository.findByTripLocationId(tripLocationId);
        String prefix = "trip-locations/" + tripLocationId + "/";
        for (TripLocationImageEntity image : images) {
            imageService.deleteStoredObjectByUrlIfApplicable(image.getImagePath(), prefix);
        }
        tripLocationImageRepository.deleteAll(images);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{tripLocationId}/images/{imageId}")
    public ResponseEntity<Void> deleteImage(
            @PathVariable Long tripLocationId,
            @PathVariable Long imageId,
            @AuthenticationPrincipal Jwt jwt) {
        long callerId = Long.parseLong(jwt.getSubject());
        TripLocationEntity tripLocation =
                tripLocationRepository
                        .findByIdWithTripAndUser(tripLocationId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found."));
        long ownerId = tripLocation.getTrip().getUser().getId();
        if (ownerId != callerId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not authorized.");
        }

        TripLocationImageEntity image = tripLocationImageRepository.findById(imageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found."));
        if (!image.getTripLocation().getId().equals(tripLocationId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found.");
        }

        String prefix = "trip-locations/" + tripLocationId + "/";
        imageService.deleteStoredObjectByUrlIfApplicable(image.getImagePath(), prefix);
        tripLocationImageRepository.delete(image);
        return ResponseEntity.noContent().build();
    }
}