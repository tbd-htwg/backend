package com.tripplanning.images;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import com.tripplanning.trip.read.TripCacheEvictor;
import com.tripplanning.tripLocation.TripLocationEntity;
import com.tripplanning.tripLocation.TripLocationImageEntity;
import com.tripplanning.tripLocation.TripLocationImageRepository;
import com.tripplanning.tripLocation.TripLocationRepository;
import com.tripplanning.user.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class TripLocationPreuploadedImageController {

    private final ImageService imageService;
    private final TripLocationRepository tripLocationRepository;
    private final TripLocationImageRepository tripLocationImageRepository;
    private final UserService userService;
    private final TripCacheEvictor tripCacheEvictor;

    /** Full path on the method (like {@link com.tripplanning.social.LikeController}) avoids odd interplay with Spring Data REST base URI. */
    @PostMapping("/api/v2/trip-location-images/preuploaded")
    public ResponseEntity<?> registerPreuploaded(
            @RequestBody ImageUploadDtos.RegisterPreuploadedBody body, @AuthenticationPrincipal Jwt jwt) {
        try {
            if (body.tripLocationId() == null) {
                throw new IllegalArgumentException("tripLocationId is required.");
            }
            TripLocationEntity tripLocation =
                    tripLocationRepository
                            .findByIdWithTripAndUser(body.tripLocationId())
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found."));
            if (!userService.isCurrentUser(tripLocation.getTrip().getUser())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not authorized.");
            }

            String objectName = ImageUploadDtos.normalizedRegisteredObjectName(body.imagePath());
            Optional<String> existenceIssue = imageService.objectExistenceProblem(objectName);
            if (existenceIssue.isPresent()) {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(existenceIssue.get());
            }

            TripLocationImageEntity image =
                    TripLocationImageEntity.builder()
                            .tripLocation(tripLocation)
                            .imagePath(objectName)
                            .build();
            tripLocationImageRepository.save(image);
            tripCacheEvictor.evictForTripChange(tripLocation.getTrip().getId());
            String signedReadUrl = imageService.createSignedReadUrl(objectName);
            return ResponseEntity.ok(
                    new ImageUploadDtos.RegisterPreuploadedResponse(image.getId(), signedReadUrl, objectName));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Fehler beim Verknüpfen des Bildes: " + e.getMessage());
        }
    }
}
