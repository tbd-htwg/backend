package com.tripplanning.images;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import com.tripplanning.trip.read.TripCacheEvictor;
import com.tripplanning.tripLocation.TripLocationEntity;
import com.tripplanning.tripLocation.TripLocationImageEntity;
import com.tripplanning.tripLocation.TripLocationImageRepository;
import com.tripplanning.tripLocation.TripLocationRepository;
import com.tripplanning.user.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v2/trip-locations")
@RequiredArgsConstructor
public class TripLocationImageController {
    private final ImageService imageService;
    private final TripLocationRepository tripLocationRepository;
    private final TripLocationImageRepository tripLocationImageRepository;
    private final UserService userService;
    private final TripCacheEvictor tripCacheEvictor;

    @PostMapping("/{tripLocationId}/images")
    public ResponseEntity<?> createUploadUrl(
            @PathVariable Long tripLocationId, 
            @RequestBody ImageUploadDtos.CreateUploadRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        try {
            TripLocationEntity tripLocation = tripLocationRepository
                .findByIdWithTripAndUser(tripLocationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found."));
            if (!userService.isCurrentUser(tripLocation.getTrip().getUser())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not authorized.");
            }

            ImageService.SignedUploadInfo signedUpload =
                    imageService.createSignedUpload(
                            "trip-locations/" + tripLocationId,
                            request.fileName(),
                            request.contentType());

                TripLocationImageEntity image = TripLocationImageEntity.builder()
                    .tripLocation(tripLocation)
                    .imagePath(signedUpload.objectName())
                    .build();

                tripLocationImageRepository.save(image);
            tripCacheEvictor.evictForTripChange(tripLocation.getTrip().getId());
            String signedReadUrl = imageService.createSignedReadUrl(signedUpload.objectName());
            return ResponseEntity.ok(
                    new ImageUploadDtos.CreateUploadResponse(
                image.getId(),
                            signedUpload.uploadUrl(),
                            signedReadUrl,
                            signedUpload.objectName(),
                            signedUpload.contentType()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Fehler beim Erstellen der Upload-URL: " + e.getMessage());
        }
    }


    @GetMapping("/{tripLocationId}/images")
    public ResponseEntity<List<String>> getImages(@PathVariable Long tripLocationId) {
        List<TripLocationImageEntity> images = tripLocationImageRepository.findByTripLocationId(tripLocationId);
        
        List<String> signedReadUrls = images.stream()
                .map(img -> imageService.createSignedReadUrl(img.getImagePath()))
                .toList();

        return ResponseEntity.ok(signedReadUrls);
    }


    @DeleteMapping("/{tripLocationId}/images")
    public ResponseEntity<Void> deleteImage(
            @PathVariable Long tripLocationId, @AuthenticationPrincipal Jwt jwt) {
        TripLocationEntity tripLocation =
                tripLocationRepository
                        .findByIdWithTripAndUser(tripLocationId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found."));
        if (!userService.isCurrentUser(tripLocation.getTrip().getUser())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not authorized.");
        }
        List<TripLocationImageEntity> images = tripLocationImageRepository.findByTripLocationId(tripLocationId);
        String prefix = "trip-locations/" + tripLocationId + "/";
        for (TripLocationImageEntity image : images) {
            imageService.deleteStoredObjectByPath(image.getImagePath(), prefix);
        }
        tripLocationImageRepository.deleteAll(images);
        tripCacheEvictor.evictForTripChange(tripLocation.getTrip().getId());
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
        imageService.deleteStoredObjectByPath(image.getImagePath(), prefix);
        tripLocationImageRepository.delete(image);
        tripCacheEvictor.evictForTripChange(tripLocation.getTrip().getId());
        return ResponseEntity.noContent().build();
    }
}