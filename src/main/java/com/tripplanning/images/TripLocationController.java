package com.tripplanning.images;


import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.tripplanning.tripLocation.TripLocationEntity;
import com.tripplanning.tripLocation.TripLocationRepository;

@RestController
@RequestMapping("/api/v2/trip-locations")
@RequiredArgsConstructor
public class TripLocationController {

    private final ImageService imageService;
    private final TripLocationRepository tripLocationRepository; 

    @PostMapping("/{tripLocationId}/images")
    public ResponseEntity<?> uploadImage(
            @PathVariable Long tripLocationId, 
            @RequestParam("file") MultipartFile file) {
        try {
            String url = imageService.uploadImage(file, "trip-locations/" + tripLocationId);

            TripLocationEntity tripLocation = tripLocationRepository.findById(tripLocationId)
                    .orElseThrow(() -> new RuntimeException("Trip-Location nicht gefunden"));

            tripLocation.setImageUrl(url);
            tripLocationRepository.save(tripLocation);

            return ResponseEntity.ok(url);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Fehler beim Upload: " + e.getMessage());
        }
    }
}