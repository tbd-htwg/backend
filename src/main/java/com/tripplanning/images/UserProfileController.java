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

import com.tripplanning.user.UserEntity;
import com.tripplanning.user.UserRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v2/users")
@RequiredArgsConstructor
public class UserProfileController {
    private final ImageService imageService;
    private final UserRepository userRepository;

    @PostMapping("/{userId}/images")
    public ResponseEntity<?> createUploadUrl(
            @PathVariable Long userId, 
            @RequestBody ImageUploadDtos.CreateUploadRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        try {
            long callerId = Long.parseLong(jwt.getSubject());
            if (callerId != userId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
            }

            UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

            ImageService.SignedUploadInfo signedUpload =
                    imageService.createSignedUpload(
                            "user-profiles/" + userId,
                            request.fileName(),
                            request.contentType());

            String prefix = "user-profiles/" + userId + "/";
            imageService.deleteStoredObjectByUrlIfApplicable(user.getImagePath(), prefix);

            user.setImagePath(signedUpload.objectName());
            userRepository.save(user);

            String signedReadUrl = imageService.createSignedReadUrl(signedUpload.objectName());
            return ResponseEntity.ok(
                    new ImageUploadDtos.CreateUploadResponse(
                        null,
                            signedUpload.uploadUrl(),
                            signedReadUrl,
                            signedUpload.objectName(),
                            signedUpload.contentType()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error while creating upload URL: " + e.getMessage());
        }
    }


    @GetMapping("/{userId}/image")
    public ResponseEntity<String> getProfileImage(@PathVariable Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        
        if (user.getImagePath() == null) {
            return ResponseEntity.notFound().build();
        }

        String signedUrl = imageService.createSignedReadUrl(user.getImagePath());
        return ResponseEntity.ok(signedUrl);
    }
    

    @DeleteMapping("/{userId}/images")
    public ResponseEntity<Void> deleteProfileImage(
            @PathVariable Long userId, @AuthenticationPrincipal Jwt jwt) {
        long callerId = Long.parseLong(jwt.getSubject());
        if (callerId != userId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        }
        UserEntity user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        String prefix = "user-profiles/" + userId + "/";
        imageService.deleteStoredObjectByUrlIfApplicable(user.getImagePath(), prefix);
        user.setImagePath(null);
        userRepository.save(user);
        return ResponseEntity.noContent().build();
    }
}